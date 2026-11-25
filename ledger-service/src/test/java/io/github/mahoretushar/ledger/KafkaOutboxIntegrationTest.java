package io.github.mahoretushar.ledger;

import io.github.mahoretushar.ledger.account.AccountDtos.OpenAccountRequest;
import io.github.mahoretushar.ledger.account.AccountService;
import io.github.mahoretushar.ledger.outbox.OutboxRelay;
import io.github.mahoretushar.ledger.security.Caller;
import io.github.mahoretushar.ledger.transfer.TransferDtos.TransferRequest;
import io.github.mahoretushar.ledger.transfer.TransferService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "ledger.events.publisher=kafka",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"})
@EmbeddedKafka(partitions = 3, topics = "ledger.transfer-events")
@ActiveProfiles("test")
class KafkaOutboxIntegrationTest {

    @Autowired AccountService accounts;
    @Autowired TransferService transfers;
    @Autowired OutboxRelay relay;
    @Autowired EmbeddedKafkaBroker broker;

    @Test
    void transferEventReachesKafkaWithOwnersAndEventId() {
        var asha = new Caller("asha", false);
        UUID a = accounts.open(asha, new OpenAccountRequest("Asha", "INR", new BigDecimal("100"))).id();
        UUID b = accounts.open(new Caller("ravi", false), new OpenAccountRequest("Ravi", "INR", BigDecimal.ZERO)).id();
        UUID transferId = transfers.execute(asha, "kafka-" + UUID.randomUUID(), new TransferRequest(a, b, new BigDecimal("42"))).id();

        while (relay.relay() > 0) {
            // drain the outbox
        }

        var props = KafkaTestUtils.consumerProps(broker, "test-" + UUID.randomUUID(), false);
        props.put("auto.offset.reset", "earliest");
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("ledger.transfer-events"));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline
                    && received.stream().noneMatch(r -> r.key().equals(transferId.toString()))) {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            }
            var record = received.stream().filter(r -> r.key().equals(transferId.toString())).findFirst().orElseThrow();
            assertThat(record.value())
                    .contains("\"type\":\"TransferCompleted\"")
                    .contains("\"fromOwner\":\"asha\"")
                    .contains("\"toOwner\":\"ravi\"")
                    .contains("\"eventId\"");
            assertThat(new String(record.headers().lastHeader("eventType").value())).isEqualTo("TransferCompleted");
        }
    }
}
