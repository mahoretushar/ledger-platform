package io.github.mahoretushar.ledger;

import com.jayway.jsonpath.JsonPath;
import io.github.mahoretushar.ledger.outbox.OutboxEventRepository;
import io.github.mahoretushar.ledger.outbox.OutboxRelay;
import io.github.mahoretushar.ledger.transfer.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired LedgerEntryRepository entries;
    @Autowired OutboxEventRepository outbox;
    @Autowired OutboxRelay relay;

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/v1/accounts/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void transferMovesMoneyAndWritesBothLedgerLegs() throws Exception {
        String asha = token("asha");
        String ravi = token("ravi");
        String a = open(asha, "Asha", "1000.00");
        String b = open(ravi, "Ravi", "50.00");

        String transferId = transfer(asha, "key-" + UUID.randomUUID(), a, b, "250.50", 201);

        mvc.perform(get("/api/v1/accounts/" + a).header("Authorization", asha))
                .andExpect(jsonPath("$.balance").value(749.5));
        mvc.perform(get("/api/v1/accounts/" + b).header("Authorization", ravi))
                .andExpect(jsonPath("$.balance").value(300.5));
        assertThat(entries.findByTransferId(UUID.fromString(transferId))).hasSize(2);
        assertThat(entries.netPosition()).isEqualByComparingTo(BigDecimal.ZERO);

        mvc.perform(get("/api/v1/accounts/" + a + "/statement").header("Authorization", asha))
                .andExpect(jsonPath("$[0].direction").value("DEBIT"));
    }

    @Test
    void customerCannotSpendFromSomeoneElsesAccount() throws Exception {
        String asha = token("asha");
        String ravi = token("ravi");
        String ashaAccount = open(asha, "Asha", "1000.00");
        String raviAccount = open(ravi, "Ravi", "0.00");

        transfer(ravi, "key-" + UUID.randomUUID(), ashaAccount, raviAccount, "500.00", 403);
        mvc.perform(get("/api/v1/accounts/" + ashaAccount).header("Authorization", ravi))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/accounts/" + ashaAccount).header("Authorization", asha))
                .andExpect(jsonPath("$.balance").value(1000.0));
    }

    @Test
    void retryWithSameIdempotencyKeyDoesNotChargeTwice() throws Exception {
        String asha = token("asha");
        String a = open(asha, "Asha", "500.00");
        String b = open(token("ravi"), "Ravi", "0.00");
        String key = "key-" + UUID.randomUUID();

        String first = transfer(asha, key, a, b, "100.00", 201);
        String second = transfer(asha, key, a, b, "100.00", 201);

        assertThat(second).isEqualTo(first);
        mvc.perform(get("/api/v1/accounts/" + a).header("Authorization", asha))
                .andExpect(jsonPath("$.balance").value(400.0));
    }

    @Test
    void reusingKeyWithDifferentBodyIsAConflict() throws Exception {
        String asha = token("asha");
        String a = open(asha, "Asha", "500.00");
        String b = open(token("ravi"), "Ravi", "0.00");
        String key = "key-" + UUID.randomUUID();
        transfer(asha, key, a, b, "100.00", 201);
        transfer(asha, key, a, b, "999.00", 409);
    }

    @Test
    void insufficientFundsReturnsProblemDetail() throws Exception {
        String asha = token("asha");
        String a = open(asha, "Asha", "10.00");
        String b = open(token("ravi"), "Ravi", "0.00");
        mvc.perform(post("/api/v1/transfers").header("Authorization", asha)
                        .header("Idempotency-Key", "key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(a, b, "10.01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void adminCanReverseATransferExactlyOnce() throws Exception {
        String asha = token("asha");
        String ravi = token("ravi");
        String admin = token("admin");
        String a = open(asha, "Asha", "300.00");
        String b = open(ravi, "Ravi", "0.00");
        String id = transfer(asha, "key-" + UUID.randomUUID(), a, b, "120.00", 201);

        mvc.perform(post("/api/v1/transfers/" + id + "/reversal").header("Authorization", asha))
                .andExpect(status().isForbidden());

        MvcResult first = mvc.perform(post("/api/v1/transfers/" + id + "/reversal").header("Authorization", admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversalOf").value(id)).andReturn();
        MvcResult again = mvc.perform(post("/api/v1/transfers/" + id + "/reversal").header("Authorization", admin))
                .andExpect(status().isCreated()).andReturn();
        assertThat(JsonPath.<String>read(again.getResponse().getContentAsString(), "$.id"))
                .isEqualTo(JsonPath.read(first.getResponse().getContentAsString(), "$.id"));

        mvc.perform(get("/api/v1/accounts/" + a).header("Authorization", asha))
                .andExpect(jsonPath("$.balance").value(300.0));
        mvc.perform(get("/api/v1/transfers/" + id).header("Authorization", asha))
                .andExpect(jsonPath("$.status").value("REVERSED"));
    }

    @Test
    void freezeRequiresAdminScope() throws Exception {
        String asha = token("asha");
        String a = open(asha, "Asha", "1.00");
        mvc.perform(post("/api/v1/accounts/" + a + "/freeze").header("Authorization", asha))
                .andExpect(status().isForbidden());
    }

    @Test
    void outboxRelayPublishesPendingEvents() throws Exception {
        String asha = token("asha");
        String a = open(asha, "Asha", "100.00");
        String b = open(token("ravi"), "Ravi", "0.00");
        transfer(asha, "key-" + UUID.randomUUID(), a, b, "1.00", 201);
        assertThat(outbox.countByPublishedAtIsNull()).isPositive();
        while (relay.relay() > 0) {
            // drain everything earlier tests left behind as well
        }
        assertThat(outbox.countByPublishedAtIsNull()).isZero();
    }

    private String token(String user) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/token").with(httpBasic(user, user + "-pass")))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private String open(String bearer, String owner, String opening) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/accounts").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerName":"%s","currency":"INR","openingBalance":%s}""".formatted(owner, opening)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    private String transfer(String bearer, String key, String from, String to, String amount, int expected)
            throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/transfers").header("Authorization", bearer)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(from, to, amount)))
                .andExpect(status().is(expected)).andReturn();
        return expected == 201 ? JsonPath.read(res.getResponse().getContentAsString(), "$.id") : null;
    }

    private static String body(String from, String to, String amount) {
        return """
                {"fromAccountId":"%s","toAccountId":"%s","amount":%s}""".formatted(from, to, amount);
    }
}
