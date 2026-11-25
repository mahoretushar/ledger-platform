package io.github.mahoretushar.ledger.transfer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TransferDtos {

    private TransferDtos() {
    }

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount) {
    }

    public record TransferResponse(UUID id, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
                                   String currency, String status, UUID reversalOf, Instant createdAt) {
        static TransferResponse from(Transfer t) {
            return new TransferResponse(t.getId(), t.getFromAccountId(), t.getToAccountId(), t.getAmount(),
                    t.getCurrency(), t.getStatus().name(), t.getReversalOf(), t.getCreatedAt());
        }
    }

    /** Event contract published to Kafka; consumers depend on these field names. */
    public record TransferEvent(UUID eventId, String type, UUID transferId,
                                UUID fromAccountId, String fromOwner, UUID toAccountId, String toOwner,
                                BigDecimal amount, String currency, UUID reversalOf, Instant occurredAt) {
    }
}
