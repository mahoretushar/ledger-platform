package io.github.mahoretushar.ledger.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record OpenAccountRequest(
            @NotBlank @Size(max = 120) String ownerName,
            @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code") String currency,
            @NotNull @DecimalMin("0.00") BigDecimal openingBalance) {
    }

    public record AccountResponse(UUID id, String ownerUsername, String ownerName, String currency,
                                  BigDecimal balance, String status, Instant createdAt) {
        static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getOwnerUsername(), a.getOwnerName(), a.getCurrency(),
                    a.getBalance(), a.getStatus().name(), a.getCreatedAt());
        }
    }

    public record StatementLine(UUID transferId, String direction, BigDecimal amount,
                                BigDecimal balanceAfter, Instant at) {
    }
}
