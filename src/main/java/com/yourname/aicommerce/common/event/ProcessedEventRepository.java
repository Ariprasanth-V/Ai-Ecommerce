package com.yourname.aicommerce.common.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.ProcessedEventId> {
    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
