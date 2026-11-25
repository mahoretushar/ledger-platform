package io.github.mahoretushar.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByEventIdAndRecipient(UUID eventId, String recipient);

    List<Notification> findTop50ByRecipientOrderByCreatedAtDesc(String recipient);

    long countByEventId(UUID eventId);
}
