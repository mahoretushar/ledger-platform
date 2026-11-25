package io.github.mahoretushar.ledger.transfer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    List<LedgerEntry> findByTransferId(UUID transferId);

    /** Sum of credits minus debits; must always be zero across the whole ledger. */
    @Query("""
            select coalesce(sum(case when e.direction = 'CREDIT' then e.amount else -e.amount end), 0)
            from LedgerEntry e""")
    BigDecimal netPosition();
}
