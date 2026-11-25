package io.github.mahoretushar.notification;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    public record NotificationView(UUID id, UUID accountId, UUID transferId, String direction, BigDecimal amount,
                                   String currency, String message, Instant createdAt) {
    }

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Callers only ever see their own notifications; the recipient comes from the token, not a parameter. */
    @GetMapping
    public List<NotificationView> mine(Authentication auth) {
        return repository.findTop50ByRecipientOrderByCreatedAtDesc(auth.getName()).stream()
                .map(n -> new NotificationView(n.getId(), n.getAccountId(), n.getTransferId(), n.getDirection(),
                        n.getAmount(), n.getCurrency(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}
