package io.github.mahoretushar.notification;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String recipient;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(nullable = false)
    private String direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID eventId, String recipient, UUID accountId, UUID transferId, String direction,
                        BigDecimal amount, String currency, String message) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.recipient = recipient;
        this.accountId = accountId;
        this.transferId = transferId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getRecipient() { return recipient; }
    public UUID getAccountId() { return accountId; }
    public UUID getTransferId() { return transferId; }
    public String getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
