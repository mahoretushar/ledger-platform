package io.github.mahoretushar.notification;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConfig {

    /**
     * Declared here as well as in the ledger service: whichever starts first creates the topic with the right
     * partition count, instead of the broker auto-creating a 1-partition topic when this consumer subscribes.
     */
    @Bean
    NewTopic transferEventsTopic(@Value("${notification.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic deadLetterTopic(@Value("${notification.topic}") String topic) {
        return TopicBuilder.name(topic + "-dlt").partitions(3).replicas(1).build();
    }

    /**
     * Transient failures (database down, deadlock) are retried 3 times one second apart; after that, or at once
     * for an invalid message, the record is published to "<topic>-dlt" so one bad message never blocks a partition.
     */
    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        var handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
