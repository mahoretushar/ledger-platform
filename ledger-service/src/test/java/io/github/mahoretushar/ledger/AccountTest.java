package io.github.mahoretushar.ledger;

import io.github.mahoretushar.ledger.account.Account;
import io.github.mahoretushar.ledger.common.BusinessRuleException;
import io.github.mahoretushar.ledger.security.Caller;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void debitBeyondBalanceIsRejected() {
        var account = new Account("asha", "Asha", "INR", new BigDecimal("100.00"));
        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("insufficient funds");
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void frozenAccountCannotMoveMoney() {
        var account = new Account("ravi", "Ravi", "INR", new BigDecimal("50"));
        account.freeze();
        assertThatThrownBy(() -> account.credit(BigDecimal.ONE)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void onlyOwnerOrAdminCanAccessAnAccount() {
        var account = new Account("asha", "Asha", "INR", BigDecimal.TEN);
        new Caller("asha", false).requireAccess(account);
        new Caller("admin", true).requireAccess(account);
        assertThatThrownBy(() -> new Caller("ravi", false).requireAccess(account))
                .isInstanceOf(AccessDeniedException.class);
    }
}
