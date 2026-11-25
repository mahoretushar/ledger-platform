package io.github.mahoretushar.ledger.transfer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    Optional<Transfer> findByReversalOf(UUID reversalOf);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transfer t where t.id = :id")
    Optional<Transfer> findByIdForUpdate(UUID id);
}
