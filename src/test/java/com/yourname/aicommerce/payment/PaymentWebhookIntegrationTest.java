package com.yourname.aicommerce.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourname.aicommerce.auth.Role;
import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.auth.UserRepository;
import com.yourname.aicommerce.catalog.Category;
import com.yourname.aicommerce.catalog.CategoryRepository;
import com.yourname.aicommerce.catalog.Product;
import com.yourname.aicommerce.catalog.ProductRepository;
import com.yourname.aicommerce.common.event.ProcessedEvent;
import com.yourname.aicommerce.common.event.ProcessedEventRepository;
import com.yourname.aicommerce.order.Order;
import com.yourname.aicommerce.order.OrderItem;
import com.yourname.aicommerce.order.OrderRepository;
import com.yourname.aicommerce.order.OrderStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentWebhookIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.razorpay.webhook-secret}")
    private String webhookSecret;

    private User testUser;
    private Product testProduct;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        processedEventRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .active(true)
                .build());

        Category category = categoryRepository.save(Category.builder().name("Electronics").build());

        testProduct = productRepository.save(Product.builder()
                .name("Laptop")
                .description("Gaming Laptop")
                .price(new BigDecimal("999.99"))
                .sku("LAPTOP-001")
                .stockQuantity(5)
                .category(category)
                .active(true)
                .build());

        testOrder = Order.builder()
                .user(testUser)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("999.99"))
                .build();
        testOrder.addItem(OrderItem.builder()
                .product(testProduct)
                .quantity(1)
                .unitPrice(testProduct.getPrice())
                .build());
        testOrder = orderRepository.save(testOrder);
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    void webhook_missingOrInvalidSignature_returns400BadRequest() throws Exception {
        String payload = """
                {
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_tampered_123",
                        "order_id": "order_123",
                        "amount": 99999,
                        "notes": {
                          "orderId": "%d"
                        }
                      }
                    }
                  }
                }
                """.formatted(testOrder.getId());

        // Test 1: Missing signature header
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or missing Razorpay webhook signature"));

        // Test 2: Invalid/tampered signature header
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", "invalid_hmac_signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or missing Razorpay webhook signature"));

        // Verify order status unchanged
        Order currentOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(currentOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void webhook_validSignature_paymentCaptured_updatesOrderToConfirmedAndRecordsProcessedEvent() throws Exception {
        String payload = """
                {
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_valid_123456",
                        "order_id": "order_razorpay_789",
                        "amount": 99999,
                        "notes": {
                          "orderId": "%d"
                        }
                      }
                    }
                  }
                }
                """.formatted(testOrder.getId());

        String validSignature = calculateHmacSha256(payload, webhookSecret);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Webhook processed successfully"));

        // Verify order updated to CONFIRMED
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Verify ProcessedEvent stored with payload.payment.entity.id as event_id
        boolean eventProcessed = processedEventRepository.existsById(
                new ProcessedEvent.ProcessedEventId("pay_valid_123456", "razorpay-webhook"));
        assertThat(eventProcessed).isTrue();
    }

    @Test
    void webhook_validSignature_paymentFailed_cancelsOrderAndReleasesStock() throws Exception {
        String payload = """
                {
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_failed_123456",
                        "order_id": "order_razorpay_789",
                        "amount": 99999,
                        "notes": {
                          "orderId": "%d"
                        }
                      }
                    }
                  }
                }
                """.formatted(testOrder.getId());

        String validSignature = calculateHmacSha256(payload, webhookSecret);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Verify order status updated to CANCELLED
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Verify stock released back (from 5 to 6)
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(6);
    }

    private String calculateHmacSha256(String data, String key) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
