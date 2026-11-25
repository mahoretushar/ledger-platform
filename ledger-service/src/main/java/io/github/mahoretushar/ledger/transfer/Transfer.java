package io.github.mahoretushar.ledger.transfer;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer")
public class Transfer {

    public enum Status { COMPLETED, REVERSED }

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reversal_of", unique = true)
    private UUID reversalOf;

    protected Transfer() {
    }

    public Transfer(String idempotencyKey, String requestHash, UUID from, UUID to,
                    BigDecimal amount, String currency, UUID reversalOf) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.fromAccountId = from;
        this.toAccountId = to;
        this.amount = amount;
        this.currency = currency;
        this.status = Status.COMPLETED;
        this.createdAt = Instant.now();
        this.reversalOf = reversalOf;
    }

    public void markReversed() {
        this.status = Status.REVERSED;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public UUID getFromAccountId() { return fromAccountId; }
    public UUID getToAccountId() { return toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getReversalOf() { return reversalOf; }
}
