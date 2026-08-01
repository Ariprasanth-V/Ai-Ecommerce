package com.yourname.aicommerce.notification;

import com.yourname.aicommerce.common.event.ProcessedEvent;
import com.yourname.aicommerce.common.event.ProcessedEventRepository;
import com.yourname.aicommerce.order.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulates Notification microservice consumer listening for order events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private static final String CONSUMER_GROUP = "notification-service-group";
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(
            topics = "order-events",
            groupId = CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (processedEventRepository.existsByEventIdAndConsumerGroup(event.getEventId(), CONSUMER_GROUP)) {
            log.info("[NotificationService] Duplicate event {} already processed. Skipping.", event.getEventId());
            return;
        }

        log.info("[NotificationService] Sending order confirmation email for order #{} to user #{} (Total: ${})",
                event.getOrderId(), event.getUserId(), event.getTotalAmount());

        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(event.getEventId())
                .consumerGroup(CONSUMER_GROUP)
                .build());
    }
}
