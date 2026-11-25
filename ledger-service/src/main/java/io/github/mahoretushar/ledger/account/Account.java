package io.github.mahoretushar.ledger.account;

import io.github.mahoretushar.ledger.common.BusinessRuleException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account")
public class Account {

    public enum Status { ACTIVE, FROZEN }

    @Id
    private UUID id;

    @Column(name = "owner_username", nullable = false)
    private String ownerUsername;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String ownerUsername, String ownerName, String currency, BigDecimal openingBalance) {
        this.id = UUID.randomUUID();
        this.ownerUsername = ownerUsername;
        this.ownerName = ownerName;
        this.currency = currency;
        this.balance = openingBalance;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void debit(BigDecimal amount) {
        requireActive();
        if (balance.compareTo(amount) < 0) {
            throw new BusinessRuleException("INSUFFICIENT_FUNDS",
                    "Account " + id + " has insufficient funds");
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requireActive();
        balance = balance.add(amount);
    }

    public void freeze() {
        status = Status.FROZEN;
    }

    private void requireActive() {
        if (status != Status.ACTIVE) {
            throw new BusinessRuleException("ACCOUNT_FROZEN", "Account " + id + " is frozen");
        }
    }

    public UUID getId() { return id; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getOwnerName() { return ownerName; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public Status getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
