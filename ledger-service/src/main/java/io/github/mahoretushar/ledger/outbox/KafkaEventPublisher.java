package io.github.mahoretushar.ledger.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Configuration
@ConditionalOnProperty(name = "ledger.events.publisher", havingValue = "kafka", matchIfMissing = true)
class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    KafkaEventPublisher(KafkaTemplate<String, String> kafka, @Value("${ledger.events.topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    @Bean
    NewTopic transferEventsTopic() {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    /** Waits for the broker ack so a failed send leaves the outbox row unpublished for the next poll. */
    @Override
    public void publish(String eventType, String key, String payload) {
        var record = new ProducerRecord<>(topic, key, payload);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + eventType, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka publish failed for " + eventType + " " + key, e);
        }
    }
}
