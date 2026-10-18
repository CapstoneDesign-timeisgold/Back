package jiki.jiki.settlement;

import jiki.jiki.config.ConflictException;
import jiki.jiki.config.PasswordConfig;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.SystemAccountType;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({SettlementAccountService.class, PasswordConfig.class})
class SettlementAccountServiceTest {
    @Autowired SettlementAccountService accounts;
    @Autowired UserRepository users;

    @Test void createsSystemAccountOnceAndPreservesItsBalanceOnRestart() {
        accounts.initialize(null);
        SiteUser account = accounts.getAccount();
        Long id = account.getId();
        assertEquals("admin", account.getUsername());
        assertEquals(0, account.getMoney());
        assertTrue(account.isSystemAccount());
        account.setMoney(-2);
        users.saveAndFlush(account);

        accounts.initialize(null);
        assertEquals(1, users.count());
        assertEquals(id, accounts.getAccount().getId());
        assertEquals(-2, accounts.getAccount().getMoney());
    }

    @Test void neverAdoptsAnExistingAdminBasedOnItsName() {
        SiteUser member = member("admin");
        assertThrows(IllegalStateException.class, () -> accounts.initialize(null));
        assertNull(member.getSystemAccountType());
        assertThrows(ConflictException.class, accounts::getAccount);
        assertEquals(1, users.count());
    }

    @Test void explicitlyAdoptsVerifiedIdWithoutChangingExistingFinancialOrLoginData() {
        SiteUser member = member("treasury");
        accounts.initialize(member.getId());
        SiteUser account = accounts.getAccount();
        assertEquals(member.getId(), account.getId());
        assertEquals("treasury", account.getUsername());
        assertEquals("existing-password-hash", account.getPassword());
        assertEquals(1234, account.getMoney());
        assertEquals(1, users.count());
    }

    @Test void refusesMissingOrInvalidConfiguredIdsWithoutCreatingAnAccount() {
        assertThrows(IllegalStateException.class, () -> accounts.initialize(999L));
        assertThrows(IllegalStateException.class, () -> accounts.initialize(0L));
        assertEquals(0, users.count());
    }

    @Test void refusesAConflictingConfiguredIdInsteadOfSwitchingAccounts() {
        accounts.initialize(null);
        Long original = accounts.getAccount().getId();
        SiteUser other = member("other");
        assertThrows(IllegalStateException.class, () -> accounts.initialize(other.getId()));
        assertEquals(original, accounts.getAccount().getId());
        assertNull(other.getSystemAccountType());
    }

    @Test void databaseRejectsMultipleSettlementAccounts() {
        accounts.initialize(null);
        SiteUser duplicate = member("duplicate");
        duplicate.setSystemAccountType(SystemAccountType.SETTLEMENT);
        assertThrows(DataIntegrityViolationException.class, () -> users.saveAndFlush(duplicate));
    }

    private SiteUser member(String username) {
        SiteUser user = new SiteUser();
        user.setUsername(username);
        user.setNickname(username);
        user.setEmail(username + "@example.test");
        user.setPassword("existing-password-hash");
        user.setMoney(1234);
        return users.saveAndFlush(user);
    }
}
