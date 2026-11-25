package io.github.mahoretushar.ledger.transfer;

import io.github.mahoretushar.ledger.account.Account;
import io.github.mahoretushar.ledger.account.AccountRepository;
import io.github.mahoretushar.ledger.common.BusinessRuleException;
import io.github.mahoretushar.ledger.common.NotFoundException;
import io.github.mahoretushar.ledger.outbox.OutboxEvent;
import io.github.mahoretushar.ledger.outbox.OutboxEventRepository;
import io.github.mahoretushar.ledger.security.Caller;
import io.github.mahoretushar.ledger.transfer.TransferDtos.TransferEvent;
import io.github.mahoretushar.ledger.transfer.TransferDtos.TransferRequest;
import io.github.mahoretushar.ledger.transfer.TransferDtos.TransferResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository entries;
    private final OutboxEventRepository outbox;
    private final JsonMapper json;

    public TransferService(AccountRepository accounts, TransferRepository transfers, LedgerEntryRepository entries,
                           OutboxEventRepository outbox, JsonMapper json) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.entries = entries;
        this.outbox = outbox;
        this.json = json;
    }

    /**
     * Moves money atomically: two row locks, two balance updates, two ledger legs and one
     * outbox event, all in one database transaction.
     */
    @Transactional
    public TransferResponse execute(Caller caller, String idempotencyKey, TransferRequest request) {
        BigDecimal amount = request.amount().setScale(4, RoundingMode.HALF_EVEN);
        String hash = hash(request.fromAccountId(), request.toAccountId(), amount);

        var existing = transfers.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleException("SAME_ACCOUNT", "Source and destination must differ");
        }
        Transfer transfer = post(caller, request.fromAccountId(), request.toAccountId(), amount,
                idempotencyKey, hash, null);
        return TransferResponse.from(transfer);
    }

    /** Compensating transfer: moves the money back and marks the original REVERSED. Admin only. */
    @Transactional
    public TransferResponse reverse(Caller caller, UUID transferId) {
        Transfer original = transfers.findByIdForUpdate(transferId)
                .orElseThrow(() -> new NotFoundException("Transfer " + transferId + " not found"));
        var already = transfers.findByReversalOf(transferId);
        if (already.isPresent()) {
            return TransferResponse.from(already.get());
        }
        if (original.getReversalOf() != null) {
            throw new BusinessRuleException("CANNOT_REVERSE_REVERSAL", "A reversal cannot itself be reversed");
        }
        String key = "reversal:" + transferId;
        Transfer reversal = post(caller, original.getToAccountId(), original.getFromAccountId(), original.getAmount(),
                key, hash(original.getToAccountId(), original.getFromAccountId(), original.getAmount()), transferId);
        original.markReversed();
        return TransferResponse.from(reversal);
    }

    @Transactional(readOnly = true)
    public TransferResponse get(Caller caller, UUID id) {
        Transfer t = transfers.findById(id).orElseThrow(() -> new NotFoundException("Transfer " + id + " not found"));
        if (!caller.admin()) {
            boolean party = accounts.findById(t.getFromAccountId()).map(a -> a.getOwnerUsername().equals(caller.username())).orElse(false)
                    || accounts.findById(t.getToAccountId()).map(a -> a.getOwnerUsername().equals(caller.username())).orElse(false);
            if (!party) {
                throw new NotFoundException("Transfer " + id + " not found");
            }
        }
        return TransferResponse.from(t);
    }

    /** Used after a unique-key race: the other request won, so return its result. */
    @Transactional(readOnly = true)
    public TransferResponse replayAfterRace(String idempotencyKey, TransferRequest request) {
        BigDecimal amount = request.amount().setScale(4, RoundingMode.HALF_EVEN);
        var winner = transfers.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency race without a stored transfer"));
        return replay(winner, hash(request.fromAccountId(), request.toAccountId(), amount));
    }

    private Transfer post(Caller caller, UUID fromId, UUID toId, BigDecimal amount, String key, String hash,
                          UUID reversalOf) {
        // Lock both rows in a fixed (UUID) order so two opposite transfers cannot deadlock.
        UUID first = fromId.compareTo(toId) < 0 ? fromId : toId;
        UUID second = first.equals(fromId) ? toId : fromId;
        Account a = lock(first);
        Account b = lock(second);
        Account from = a.getId().equals(fromId) ? a : b;
        Account to = from == a ? b : a;

        caller.requireAccess(from);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException("CURRENCY_MISMATCH", "Cross-currency transfers are not supported");
        }

        from.debit(amount);
        to.credit(amount);

        var transfer = transfers.save(new Transfer(key, hash, from.getId(), to.getId(), amount, from.getCurrency(),
                reversalOf));
        entries.save(new LedgerEntry(transfer.getId(), from.getId(), LedgerEntry.Direction.DEBIT, amount, from.getBalance()));
        entries.save(new LedgerEntry(transfer.getId(), to.getId(), LedgerEntry.Direction.CREDIT, amount, to.getBalance()));

        UUID eventId = UUID.randomUUID();
        String type = reversalOf == null ? "TransferCompleted" : "TransferReversed";
        var event = new TransferEvent(eventId, type, transfer.getId(), from.getId(), from.getOwnerUsername(),
                to.getId(), to.getOwnerUsername(), amount, from.getCurrency(), reversalOf, Instant.now());
        outbox.save(new OutboxEvent(eventId, transfer.getId(), type, json.writeValueAsString(event)));
        return transfer;
    }

    private TransferResponse replay(Transfer stored, String requestHash) {
        if (!stored.getRequestHash().equals(requestHash)) {
            throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different request body");
        }
        return TransferResponse.from(stored);
    }

    private Account lock(UUID id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Account " + id + " not found"));
    }

    static String hash(UUID from, UUID to, BigDecimal amount) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((from + "|" + to + "|" + amount.toPlainString()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
