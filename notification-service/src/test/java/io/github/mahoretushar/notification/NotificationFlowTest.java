package io.github.mahoretushar.notification;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = {"ledger.transfer-events", "ledger.transfer-events-dlt"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationFlowTest {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired NotificationRepository repository;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired MockMvc mvc;

    @Test
    void eventCreatesOneNotificationPerPartyAndDuplicatesAreIgnored() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = event(eventId, "asha-" + eventId, "ravi-" + eventId);

        kafka.send("ledger.transfer-events", "t1", payload);
        kafka.send("ledger.transfer-events", "t1", payload);   // redelivery / duplicate publish

        await(() -> repository.countByEventId(eventId) == 2);
        Thread.sleep(1500);   // give the duplicate time to be processed
        assertThat(repository.countByEventId(eventId)).isEqualTo(2);
    }

    @Test
    void callerSeesOnlyTheirOwnNotifications() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payer = "meera-" + eventId;
        kafka.send("ledger.transfer-events", "t2", event(eventId, payer, "kunal-" + eventId));
        await(() -> repository.countByEventId(eventId) == 2);

        mvc.perform(get("/api/v1/notifications").with(jwt().jwt(j -> j.subject(payer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.startsWith("250.00 INR debited")));
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedMessageGoesToDeadLetterTopicWithoutBlockingTheNextOne() throws Exception {
        kafka.send("ledger.transfer-events", "bad", "{not json");
        UUID eventId = UUID.randomUUID();
        kafka.send("ledger.transfer-events", "bad", event(eventId, "om-" + eventId, "isha-" + eventId));

        var props = KafkaTestUtils.consumerProps(broker, "dlt-" + UUID.randomUUID(), false);
        props.put("auto.offset.reset", "earliest");
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("ledger.transfer-events-dlt"));
            boolean found = false;
            long deadline = System.currentTimeMillis() + 15_000;
            while (!found && System.currentTimeMillis() < deadline) {
                for (var r : consumer.poll(Duration.ofMillis(500))) {
                    found |= r.value().equals("{not json");
                }
            }
            assertThat(found).as("bad message published to the DLT").isTrue();
        }
        await(() -> repository.countByEventId(eventId) == 2);
    }

    private static String event(UUID eventId, String fromOwner, String toOwner) {
        return """
                {"eventId":"%s","type":"TransferCompleted","transferId":"%s","fromAccountId":"%s","fromOwner":"%s",
                 "toAccountId":"%s","toOwner":"%s","amount":250.0,"currency":"INR","reversalOf":null,
                 "occurredAt":"2026-11-26T10:00:00Z"}"""
                .formatted(eventId, UUID.randomUUID(), UUID.randomUUID(), fromOwner, UUID.randomUUID(), toOwner);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within 15s");
            }
            Thread.sleep(200);
        }
    }
}
