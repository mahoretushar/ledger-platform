package io.github.mahoretushar.ledger.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * SKIP LOCKED lets several service instances relay in parallel: each one claims a different
     * batch of rows instead of blocking on, or double-publishing, the same rows.
     */
    @Query(value = """
            select * from outbox_event
            where published_at is null
            order by created_at
            limit 100
            for update skip locked""", nativeQuery = true)
    List<OutboxEvent> claimBatch();

    long countByPublishedAtIsNull();
}
