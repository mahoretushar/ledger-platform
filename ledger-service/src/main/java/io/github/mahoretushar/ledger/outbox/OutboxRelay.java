package io.github.mahoretushar.ledger.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls unpublished outbox rows and hands them to the broker. Delivery is at-least-once: if the
 * transaction fails after some sends, those rows are sent again, so consumers must be idempotent.
 */
@Component
public class OutboxRelay {

    private final OutboxEventRepository repository;
    private final EventPublisher publisher;

    public OutboxRelay(OutboxEventRepository repository, EventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${ledger.outbox.poll-interval-ms}")
    @Transactional
    public int relay() {
        var batch = repository.claimBatch();
        for (OutboxEvent event : batch) {
            publisher.publish(event.getEventType(), event.getAggregateId().toString(), event.getPayload());
            event.markPublished();
        }
        return batch.size();
    }
}
