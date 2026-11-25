package io.github.mahoretushar.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Consumer-side copy of the ledger's event contract. Unknown fields are ignored, so the producer can add fields. */
public record TransferEvent(UUID eventId, String type, UUID transferId,
                            UUID fromAccountId, String fromOwner, UUID toAccountId, String toOwner,
                            BigDecimal amount, String currency, UUID reversalOf, Instant occurredAt) {

    void validate() {
        if (eventId == null || transferId == null || fromOwner == null || toOwner == null || amount == null) {
            throw new InvalidEventException("Transfer event is missing required fields");
        }
    }
}
