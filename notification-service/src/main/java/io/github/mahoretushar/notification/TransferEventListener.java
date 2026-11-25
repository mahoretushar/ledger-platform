package io.github.mahoretushar.notification;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TransferEventListener {

    private final NotificationService service;
    private final JsonMapper json;

    public TransferEventListener(NotificationService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = "${notification.topic}", concurrency = "3")
    public void onMessage(String payload) {
        TransferEvent event;
        try {
            event = json.readValue(payload, TransferEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("Unreadable transfer event: " + e.getOriginalMessage());
        }
        service.handle(event);
    }
}
