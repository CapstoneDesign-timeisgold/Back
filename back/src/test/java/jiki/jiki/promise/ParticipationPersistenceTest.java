package jiki.jiki.promise;

import jakarta.persistence.EntityManager;
import jiki.jiki.config.ConflictException;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({PromiseService.class, ParticipationPersistenceTest.FixedTime.class})
class ParticipationPersistenceTest {
    @TestConfiguration
    static class FixedTime {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-06T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired private PromiseService service;
    @Autowired private UserRepository users;
    @Autowired private ParticipantRepository participants;
    @Autowired private PromiseRepository promises;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsDeadlineAcceptanceLockAndCancellationAcrossReloads() {
        users.save(user("host"));
        users.save(user("guest"));
        Long id = service.createPromise("host", PromiseCreateDto.builder()
                .date("2026-09-06").time("12:30").title("점심").penalty(1000)
                .participationDeadline(LocalDateTime.of(2026, 9, 6, 12, 25)).build()).getPromiseId();
        reload();

        ParticipationDeadlineDto change = new ParticipationDeadlineDto();
        change.setParticipationDeadline(LocalDateTime.of(2026, 9, 6, 12, 20));
        service.updateParticipationDeadline("host", id, change);
        reload();
        assertEquals(change.getParticipationDeadline(), promises.findById(id).orElseThrow().getParticipationDeadline());
        reload();

        service.inviteParticipant("host", ParticipantRequestDto.builder().promiseId(id).guestUsername("guest").build());
        reload();
        Long participantId = participants.findByPromiseIdAndGuestUsername(id, "guest").orElseThrow().getId();
        reload();
        service.acceptPromiseInvitation("guest", participantId);
        reload();
        assertTrue(promises.findById(id).orElseThrow().isParticipationDeadlineLocked());
        reload();
        service.cancelParticipation("guest", participantId);
        reload();
        assertEquals(ParticipantStatus.CANCELLED, participants.findById(participantId).orElseThrow().getStatus());
        assertTrue(promises.findById(id).orElseThrow().isParticipationDeadlineLocked());
        reload();
        assertTrue(service.getPromiseList("guest").isEmpty());
        PromiseDetailDto detail = service.getPromiseDetail(id, "host");
        assertFalse(detail.getParticipantUsernames().contains("guest"));
        assertEquals(ParticipantStatus.CANCELLED, detail.getParticipants().stream()
                .filter(p -> p.getUsername().equals("guest")).findFirst().orElseThrow().getStatus());
        assertThrows(ConflictException.class, () -> service.updateParticipationDeadline("host", id, change));
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    private SiteUser user(String name) {
        SiteUser user = new SiteUser();
        user.setUsername(name);
        user.setPassword("test-hash");
        user.setNickname(name);
        user.setEmail(name + "@example.com");
        return user;
    }
}
