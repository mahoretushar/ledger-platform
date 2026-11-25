package io.github.mahoretushar.ledger.account;

import io.github.mahoretushar.ledger.account.AccountDtos.AccountResponse;
import io.github.mahoretushar.ledger.account.AccountDtos.OpenAccountRequest;
import io.github.mahoretushar.ledger.account.AccountDtos.StatementLine;
import io.github.mahoretushar.ledger.security.Caller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(Authentication auth, @Valid @RequestBody OpenAccountRequest request) {
        return service.open(Caller.from(auth), request);
    }

    @GetMapping
    public List<AccountResponse> mine(Authentication auth) {
        return service.mine(Caller.from(auth));
    }

    @GetMapping("/{id}")
    public AccountResponse get(Authentication auth, @PathVariable UUID id) {
        return service.get(Caller.from(auth), id);
    }

    @GetMapping("/{id}/statement")
    public List<StatementLine> statement(Authentication auth, @PathVariable UUID id,
                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.statement(Caller.from(auth), id, page, size);
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public AccountResponse freeze(@PathVariable UUID id) {
        return service.freeze(id);
    }
}
