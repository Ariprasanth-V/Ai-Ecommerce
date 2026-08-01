-- Migration V6: Create processed_events table with composite primary key (event_id, consumer_group)
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        VARCHAR(100) NOT NULL,
    consumer_group  VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);
