package jiki.jiki.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jiki.jiki.config.ConflictException;
import jiki.jiki.config.TimeConfig;
import jiki.jiki.promise.Promise;
import jiki.jiki.user.SiteUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({SettlementService.class, TimeConfig.class, SettlementIntegrityTest.JsonConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SettlementIntegrityTest {
    @TestConfiguration static class JsonConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired SettlementService service;
    @Autowired JdbcTemplate jdbc;
    SettlementTestData data;

    @BeforeEach void resetSyntheticData() {
        for (String table : List.of("money_record", "participant", "promise", "friend", "site_user")) jdbc.update("delete from " + table);
        data = new SettlementTestData(em);
    }

    @Test void remainderIsCreditedToAdminAndEveryBalanceMatchesTheLedger() {
        Long id = tx(() -> {
            data.user("admin");
            return data.promise("remainder", List.of(data.user("late")),
                    List.of(data.user("host"), data.user("two"), data.user("three")), 1000).getId();
        });
        PromiseResultDto result = settle("host", id);
        assertEquals(1000, result.getTotalPenalty()); assertEquals(1, result.getAdminAmount());
        result.getOnTimeUsers().forEach(u -> assertEquals(333, u.getRewardAmount()));
        assertEquals(10001, money("admin")); assertEquals(5, records());
        assertLedger(50000);
    }

    @Test void allLateCreditsAdminAndRecordsTheCredit() {
        Long id = tx(() -> {
            data.user("admin");
            return data.promise("all-late", List.of(data.user("host"), data.user("late")), List.of(), 1000).getId();
        });
        assertEquals(2000, settle("host", id).getAdminAmount());
        assertEquals(12000, money("admin")); assertEquals(3, records()); assertLedger(30000);
    }

    @Test void noLateParticipantsNeedsNoAdminAndRecordsZeroRewards() {
        Long id = tx(() -> data.promise("on-time", List.of(), List.of(data.user("host"), data.user("two")), 1000).getId());
        assertEquals(0, settle("host", id).getAdminAmount());
        assertEquals(2, records()); assertLedger(20000);
    }

    @Test void adminCanBeLateAndReceiveThePoolWithoutLosingEitherEntry() {
        Long id = tx(() -> data.promise("admin-late", List.of(data.user("admin"), data.user("late")), List.of(), 1000).getId());
        settle("admin", id);
        assertEquals(11000, money("admin")); assertEquals(3, records()); assertLedger(20000);
    }

    @Test void adminOnTimeGetsCombinedRewardAndRemainderOnce() {
        Long id = tx(() -> data.promise("admin-on-time", List.of(data.user("late")),
                List.of(data.user("admin"), data.user("two"), data.user("three")), 1000).getId());
        PromiseResultDto result = settle("admin", id);
        assertEquals(1, result.getAdminAmount()); assertEquals(10334, money("admin"));
        assertEquals(4, records()); assertLedger(40000);
    }

    @Test void missingAdminRollsBackTheWholeSettlement() {
        Long id = tx(() -> data.promise("missing-admin", List.of(data.user("host")), List.of(), 1000).getId());
        assertThrows(ConflictException.class, () -> settle("host", id));
        assertUnsettled(id); assertEquals(0, records()); assertEquals(10000, money("host"));
    }

    @Test void totalOverflowIsRejectedWithoutAnyWrites() {
        Long id = tx(() -> data.promise("overflow", List.of(data.user("host"), data.user("late")), List.of(), Integer.MAX_VALUE).getId());
        assertThrows(ConflictException.class, () -> settle("host", id));
        assertUnsettled(id); assertEquals(0, records()); assertLedger(20000);
    }

    @Test void rewardOverflowRollsBackEarlierDebitAndRecord() {
        Long id = tx(() -> {
            SiteUser host = data.user("host"); host.setMoney(Integer.MAX_VALUE);
            return data.promise("overflow-reward", List.of(data.user("late")), List.of(host), 1000).getId();
        });
        assertThrows(ConflictException.class, () -> settle("host", id));
        assertEquals(10000, money("late")); assertEquals(Integer.MAX_VALUE, money("host"));
        assertUnsettled(id); assertEquals(0, records());
    }

    @Test void retryAndResultReadReturnSavedAmountsWithoutApplyingTransfersAgain() {
        Long id = simplePromise();
        PromiseResultDto first = settle("host", id);
        jdbc.update("update promise set penalty=777, title='edited' where id=?", id);
        assertEquals(first, settle("host", id));
        assertEquals(first, service.getPromiseResultDetails(id, "late"));
        assertEquals(2, records()); assertLedger(20000);
        assertEquals(9000, money("late")); assertEquals(11000, money("host"));
        assertThrows(AccessDeniedException.class, () -> settle("late", id));
        assertThrows(AccessDeniedException.class, () -> service.getPromiseResultDetails(id, "outsider"));
    }

    @Test void legacySettlementsRemainReadableButAreNeverAppliedAgain() {
        Long id = simplePromise();
        jdbc.update("update promise set is_settled=true where id=?", id);
        assertEquals(1000, service.getPromiseResultDetails(id, "host").getTotalPenalty());
        assertThrows(ConflictException.class, () -> settle("host", id));
        assertEquals(0, records()); assertLedger(20000);
    }

    @Test void duplicateLedgerConstraintRollsBackBalanceAndSettledFlag() {
        Long id = simplePromise();
        tx(() -> {
            MoneyRecord existing = new MoneyRecord();
            existing.setPromiseId(id); existing.setPromiseTitle("existing"); existing.setPenalty(true);
            existing.setUser(em.createQuery("select u from SiteUser u where u.username='late'", SiteUser.class).getSingleResult());
            em.persist(existing); return null;
        });
        assertThrows(DataIntegrityViolationException.class, () -> settle("host", id));
        assertUnsettled(id); assertEquals(10000, money("late")); assertEquals(10000, money("host"));
        assertEquals(1, records());
    }

    @Test void pendingDeclinedAndCancelledParticipantsHaveNoLedgerEntries() {
        Long id = simplePromise();
        tx(() -> {
            Promise promise = em.find(Promise.class, id);
            for (var status : List.of(jiki.jiki.promise.ParticipantStatus.PENDING,
                    jiki.jiki.promise.ParticipantStatus.DECLINED, jiki.jiki.promise.ParticipantStatus.CANCELLED)) {
                SiteUser guest = data.user(status.name()); data.participant(promise, guest, false);
                promise.getParticipants().stream().filter(p -> p.getGuest().getId().equals(guest.getId()))
                        .forEach(p -> p.setStatus(status));
            }
            return null;
        });
        assertEquals(1000, settle("host", id).getTotalPenalty());
        assertEquals(2, records()); assertLedger(50000);
    }

    private Long simplePromise() { return tx(() -> data.promise("simple", List.of(data.user("late")), List.of(data.user("host")), 1000).getId()); }
    private PromiseResultDto settle(String username, Long id) { return service.decideRewards(username, new RewardDto(id)); }
    private <T> T tx(Supplier<T> work) { return new TransactionTemplate(manager).execute(s -> work.get()); }
    private int money(String name) { return jdbc.queryForObject("select money from site_user where username=?", Integer.class, name); }
    private long records() { return jdbc.queryForObject("select count(*) from money_record", Long.class); }
    private void assertUnsettled(Long id) {
        assertFalse(jdbc.queryForObject("select is_settled from promise where id=?", Boolean.class, id));
        assertNull(jdbc.queryForObject("select settlement_result from promise where id=?", String.class, id));
    }
    private void assertLedger(long total) {
        assertEquals(total, jdbc.queryForObject("select sum(money) from site_user", Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from site_user u where u.money <> 10000 + coalesce((select sum(case when m.is_penalty then -m.amount else m.amount end) from money_record m where m.user_id=u.id),0)", Long.class));
        // Every saved balance must agree with the ledger prefix, not just the final sum.
        assertEquals(0L, jdbc.queryForObject("select count(*) from money_record r where r.balance_after_transaction <> 10000 + (select sum(case when m.is_penalty then -m.amount else m.amount end) from money_record m where m.user_id=r.user_id and m.id<=r.id)", Long.class));
    }
}
