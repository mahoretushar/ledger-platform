package io.github.mahoretushar.ledger.transfer;

import io.github.mahoretushar.ledger.security.Caller;
import io.github.mahoretushar.ledger.transfer.TransferDtos.TransferRequest;
import io.github.mahoretushar.ledger.transfer.TransferDtos.TransferResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Validated
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse create(Authentication auth,
                                   @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 80) String idempotencyKey,
                                   @Valid @RequestBody TransferRequest request) {
        try {
            return service.execute(Caller.from(auth), idempotencyKey, request);
        } catch (DataIntegrityViolationException race) {
            // Two requests with the same key committed at the same moment; the unique index chose a winner.
            return service.replayAfterRace(idempotencyKey, request);
        }
    }

    @GetMapping("/{id}")
    public TransferResponse get(Authentication auth, @PathVariable UUID id) {
        return service.get(Caller.from(auth), id);
    }

    @PostMapping("/{id}/reversal")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public TransferResponse reverse(Authentication auth, @PathVariable UUID id) {
        return service.reverse(Caller.from(auth), id);
    }
}
