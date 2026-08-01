package com.yourname.aicommerce.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.catalog.Product;
import com.yourname.aicommerce.catalog.ProductRepository;
import com.yourname.aicommerce.common.event.ProcessedEvent;
import com.yourname.aicommerce.common.event.ProcessedEventRepository;
import com.yourname.aicommerce.common.exception.ResourceNotFoundException;
import com.yourname.aicommerce.order.Order;
import com.yourname.aicommerce.order.OrderItem;
import com.yourname.aicommerce.order.OrderRepository;
import com.yourname.aicommerce.order.OrderStatus;
import com.yourname.aicommerce.order.event.OrderPaidEvent;
import com.yourname.aicommerce.payment.dto.RazorpayOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayService {

    @Value("${app.razorpay.key-id}")
    private String keyId;

    @Value("${app.razorpay.key-secret}")
    private String keySecret;

    @Value("${app.razorpay.webhook-secret}")
    private String webhookSecret;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Creates a Razorpay payment order for the given internal order.
     */
    @Transactional
    public RazorpayOrderResponse createPaymentOrder(Long orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied — order does not belong to user");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Payment order can only be created for PENDING orders");
        }

        try {
            RazorpayClient razorpayClient = new RazorpayClient(keyId, keySecret);
            JSONObject orderRequest = new JSONObject();
            // Amount in paise (1 INR = 100 paise)
            long amountInPaise = order.getTotalAmount().multiply(new BigDecimal("100")).longValue();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "receipt_order_" + order.getId());

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = razorpayOrder.get("id");

            log.info("Created Razorpay order {} for internal Order #{}", razorpayOrderId, orderId);

            return RazorpayOrderResponse.builder()
                    .orderId(order.getId())
                    .razorpayOrderId(razorpayOrderId)
                    .amount(order.getTotalAmount())
                    .currency("INR")
                    .keyId(keyId)
                    .build();

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order for Order #{}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Error initializing Razorpay order: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies the HMAC-SHA256 signature of an incoming webhook request.
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (signature == null || signature.isBlank() || payload == null) {
            return false;
        }

        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hash);

            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error calculating webhook HMAC-SHA256 signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Processes verified Razorpay webhook events with ProcessedEvent idempotency.
     */
    @Transactional
    public void processWebhook(String payload, String signature) {
        if (!verifyWebhookSignature(payload, signature)) {
            throw new IllegalArgumentException("Invalid Razorpay webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("event").asText("");
            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");

            if (paymentEntity.isMissingNode() || paymentEntity.isNull()) {
                log.warn("Razorpay webhook received without payload.payment.entity: {}", eventType);
                return;
            }

            // Extract entity ID for idempotency key
            String paymentId = paymentEntity.path("id").asText();
            if (paymentId.isBlank()) {
                log.warn("Razorpay webhook payment entity missing ID");
                return;
            }

            String consumerGroup = "razorpay-webhook";
            ProcessedEvent.ProcessedEventId eventKey = new ProcessedEvent.ProcessedEventId(paymentId, consumerGroup);

            if (processedEventRepository.existsById(eventKey)) {
                log.info("Duplicate Razorpay webhook event for paymentId '{}' ignored", paymentId);
                return;
            }

            // Extract receipt to find internal orderId
            String razorpayOrderId = paymentEntity.path("order_id").asText();
            String notesReceipt = paymentEntity.path("notes").path("receipt").asText("");
            Long orderId = extractOrderId(paymentEntity);

            if ("payment.captured".equals(eventType)) {
                handlePaymentCaptured(orderId, paymentId, razorpayOrderId, paymentEntity);
            } else if ("payment.failed".equals(eventType)) {
                handlePaymentFailed(orderId, paymentId);
            }

            // Record idempotency key
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(paymentId)
                    .consumerGroup(consumerGroup)
                    .build());

        } catch (Exception e) {
            log.error("Error processing Razorpay webhook: {}", e.getMessage(), e);
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Webhook processing error: " + e.getMessage(), e);
        }
    }

    private void handlePaymentCaptured(Long orderId, String paymentId, String razorpayOrderId, JsonNode paymentEntity) {
        if (orderId == null) {
            log.warn("Could not determine orderId for payment.captured event paymentId: {}", paymentId);
            return;
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order #{} not found for payment.captured event", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PROCESSING || order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            log.info("Order #{} is already paid/confirmed", orderId);
            return;
        }

        // Transition PENDING -> CONFIRMED
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("Order #{} status updated to CONFIRMED following payment.captured ({})", orderId, paymentId);

        // Publish OrderPaidEvent to Kafka topic after commit
        OrderPaidEvent paidEvent = OrderPaidEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(order.getId())
                .razorpayPaymentId(paymentId)
                .razorpayOrderId(razorpayOrderId)
                .amount(order.getTotalAmount())
                .paidAt(LocalDateTime.now())
                .build();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send("order-events", paidEvent.getEventId(), paidEvent);
                    log.info("Published OrderPaidEvent for order #{} to Kafka", order.getId());
                }
            });
        } else {
            kafkaTemplate.send("order-events", paidEvent.getEventId(), paidEvent);
        }
    }

    private void handlePaymentFailed(Long orderId, String paymentId) {
        if (orderId == null) {
            log.warn("Could not determine orderId for payment.failed event paymentId: {}", paymentId);
            return;
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order #{} not found for payment.failed event", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }

        // Transition PENDING -> CANCELLED & release reserved stock
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        for (OrderItem item : order.getItems()) {
            Product p = item.getProduct();
            p.setStockQuantity(p.getStockQuantity() + item.getQuantity());
            productRepository.save(p);
        }

        log.info("Order #{} cancelled and stock released following payment.failed ({})", orderId, paymentId);
    }

    private Long extractOrderId(JsonNode paymentEntity) {
        // Try reading notes.orderId or receipt format 'receipt_order_123'
        String notesOrderId = paymentEntity.path("notes").path("orderId").asText("");
        if (!notesOrderId.isBlank()) {
            try {
                return Long.parseLong(notesOrderId);
            } catch (NumberFormatException ignored) {}
        }

        String description = paymentEntity.path("description").asText("");
        if (description.contains("receipt_order_")) {
            try {
                String sub = description.substring(description.indexOf("receipt_order_") + "receipt_order_".length());
                return Long.parseLong(sub.replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {}
        }
        return null;
    }
}
