package io.github.mahoretushar.ledger.transfer;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One leg of a double-entry posting. Every transfer writes exactly one DEBIT and one CREDIT. */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    public enum Direction { DEBIT, CREDIT }

    @Id
    private UUID id;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID transferId, UUID accountId, Direction direction,
                       BigDecimal amount, BigDecimal balanceAfter) {
        this.id = UUID.randomUUID();
        this.transferId = transferId;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTransferId() { return transferId; }
    public UUID getAccountId() { return accountId; }
    public Direction getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
