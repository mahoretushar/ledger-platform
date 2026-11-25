package io.github.mahoretushar.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Idempotent: a redelivered event finds its rows already present and is skipped. */
    @Transactional
    public int handle(TransferEvent e) {
        e.validate();
        String amount = e.amount().setScale(2, RoundingMode.HALF_EVEN).toPlainString() + " " + e.currency();
        String verb = "TransferReversed".equals(e.type()) ? "reversal" : "transfer";
        int created = 0;
        created += save(e, e.fromOwner(), e.fromAccountId(), "DEBIT",
                "%s debited from your account by %s %s".formatted(amount, verb, e.transferId()));
        created += save(e, e.toOwner(), e.toAccountId(), "CREDIT",
                "%s credited to your account by %s %s".formatted(amount, verb, e.transferId()));
        if (created == 0) {
            log.info("duplicate event {} skipped", e.eventId());
        }
        return created;
    }

    private int save(TransferEvent e, String recipient, java.util.UUID accountId, String direction, String message) {
        if (repository.existsByEventIdAndRecipient(e.eventId(), recipient)) {
            return 0;
        }
        repository.save(new Notification(e.eventId(), recipient, accountId, e.transferId(), direction,
                e.amount(), e.currency(), message));
        log.info("notify {}: {}", recipient, message);   // stand-in for an email/SMS/push provider
        return 1;
    }
}
