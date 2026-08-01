package com.yourname.aicommerce.common.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Audit log of processed event IDs per consumer group to guarantee idempotency.
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEvent.ProcessedEventId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Id
    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedEventId implements Serializable {
        private String eventId;
        private String consumerGroup;
    }
}
