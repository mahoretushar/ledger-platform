package io.github.mahoretushar.ledger.account;

import io.github.mahoretushar.ledger.account.AccountDtos.AccountResponse;
import io.github.mahoretushar.ledger.account.AccountDtos.OpenAccountRequest;
import io.github.mahoretushar.ledger.account.AccountDtos.StatementLine;
import io.github.mahoretushar.ledger.common.NotFoundException;
import io.github.mahoretushar.ledger.security.Caller;
import io.github.mahoretushar.ledger.transfer.LedgerEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;

    public AccountService(AccountRepository accounts, LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @Transactional
    public AccountResponse open(Caller caller, OpenAccountRequest request) {
        var account = new Account(caller.username(), request.ownerName().trim(), request.currency(),
                request.openingBalance().setScale(4, RoundingMode.HALF_EVEN));
        return AccountResponse.from(accounts.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Caller caller, UUID id) {
        var account = find(id);
        caller.requireAccess(account);
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> mine(Caller caller) {
        return accounts.findByOwnerUsernameOrderByCreatedAt(caller.username()).stream()
                .map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<StatementLine> statement(Caller caller, UUID id, int page, int size) {
        caller.requireAccess(find(id));
        return entries.findByAccountIdOrderByCreatedAtDesc(id, PageRequest.of(page, size)).stream()
                .map(e -> new StatementLine(e.getTransferId(), e.getDirection().name(),
                        e.getAmount(), e.getBalanceAfter(), e.getCreatedAt()))
                .toList();
    }

    @Transactional
    public AccountResponse freeze(UUID id) {
        var account = find(id);
        account.freeze();
        return AccountResponse.from(account);
    }

    private Account find(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new NotFoundException("Account " + id + " not found"));
    }
}
