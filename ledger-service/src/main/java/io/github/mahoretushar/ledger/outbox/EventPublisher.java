package io.github.mahoretushar.ledger.outbox;

/** Port to the message broker. Swap the logging adapter for Kafka/RabbitMQ in production. */
public interface EventPublisher {

    void publish(String eventType, String key, String payload);
}
