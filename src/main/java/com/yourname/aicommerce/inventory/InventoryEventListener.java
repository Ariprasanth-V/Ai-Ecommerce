package com.yourname.aicommerce.inventory;

import com.yourname.aicommerce.common.event.ProcessedEvent;
import com.yourname.aicommerce.common.event.ProcessedEventRepository;
import com.yourname.aicommerce.order.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulates Inventory microservice consumer listening for order events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private static final String CONSUMER_GROUP = "inventory-service-group";
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(
            topics = "order-events",
            groupId = CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (processedEventRepository.existsByEventIdAndConsumerGroup(event.getEventId(), CONSUMER_GROUP)) {
            log.info("[InventoryService] Duplicate event {} already processed. Skipping.", event.getEventId());
            return;
        }

        log.info("[InventoryService] Processing inventory adjustment confirmation for order #{} (EventID: {})",
                event.getOrderId(), event.getEventId());

        event.getItems().forEach(item ->
                log.info("[InventoryService] Confirmed inventory deduction: product #{} ('{}'), qty: {}",
                        item.getProductId(), item.getProductName(), item.getQuantity()));

        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(event.getEventId())
                .consumerGroup(CONSUMER_GROUP)
                .build());
    }
}
