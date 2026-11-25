package io.github.mahoretushar.ledger.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used in tests and local runs without a broker (ledger.events.publisher=log). */
@Component
@ConditionalOnProperty(name = "ledger.events.publisher", havingValue = "log")
class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(String eventType, String key, String payload) {
        log.info("event={} key={} payload={}", eventType, key, payload);
    }
}
