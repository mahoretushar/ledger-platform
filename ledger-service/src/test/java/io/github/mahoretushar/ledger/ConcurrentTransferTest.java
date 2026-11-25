package io.github.mahoretushar.ledger;

import io.github.mahoretushar.ledger.account.AccountDtos.OpenAccountRequest;
import io.github.mahoretushar.ledger.account.AccountService;
import io.github.mahoretushar.ledger.common.BusinessRuleException;
import io.github.mahoretushar.ledger.security.Caller;
import io.github.mahoretushar.ledger.transfer.LedgerEntryRepository;
import io.github.mahoretushar.ledger.transfer.TransferDtos.TransferRequest;
import io.github.mahoretushar.ledger.transfer.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against real PostgreSQL row locks, so it proves the locking strategy, not an in-memory imitation. */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentTransferTest {

    @Autowired AccountService accounts;
    @Autowired TransferService transfers;
    @Autowired LedgerEntryRepository entries;

    @Test
    void parallelTransfersInARingKeepMoneyConservedWithoutDeadlocks() throws Exception {
        var admin = new Caller("admin", true);
        UUID a = accounts.open(admin, new OpenAccountRequest("Alpha", "INR", new BigDecimal("1000"))).id();
        UUID b = accounts.open(admin, new OpenAccountRequest("Beta", "INR", new BigDecimal("1000"))).id();
        UUID c = accounts.open(admin, new OpenAccountRequest("Gamma", "INR", new BigDecimal("0"))).id();

        var succeeded = new AtomicInteger();
        var rejected = new AtomicInteger();
        var tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < 200; i++) {
            UUID from = switch (i % 3) { case 0 -> a; case 1 -> b; default -> c; };
            UUID to = switch (i % 3) { case 0 -> b; case 1 -> c; default -> a; };
            tasks.add(() -> {
                try {
                    transfers.execute(admin, UUID.randomUUID().toString(),
                            new TransferRequest(from, to, new BigDecimal("7.25")));
                    succeeded.incrementAndGet();
                } catch (BusinessRuleException insufficient) {
                    rejected.incrementAndGet();
                }
                return null;
            });
        }
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var f : pool.invokeAll(tasks)) {
                f.get();   // any deadlock or unexpected error fails the test here
            }
        }

        BigDecimal total = accounts.get(admin, a).balance().add(accounts.get(admin, b).balance())
                .add(accounts.get(admin, c).balance());
        assertThat(total).isEqualByComparingTo("2000");
        assertThat(succeeded.get() + rejected.get()).isEqualTo(200);
        assertThat(succeeded.get()).isGreaterThan(100);
        assertThat(entries.netPosition()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(accounts.get(admin, c).balance()).isNotNegative();
    }
}
