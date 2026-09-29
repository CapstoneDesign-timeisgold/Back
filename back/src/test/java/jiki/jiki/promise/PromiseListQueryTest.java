package jiki.jiki.promise;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jiki.jiki.user.SiteUser;
import org.hibernate.SessionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import jiki.jiki.config.TimeConfig;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Import({PromiseService.class, TimeConfig.class})
class PromiseListQueryTest {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired PromiseService service;

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 50})
    void queryCountStaysConstantAndOnlyAcceptedPromisesAreReturned(int count) {
        SiteUser guest = user("guest");
        for (int i = 0; i < count + 3; i++) {
            SiteUser creator = user("creator-" + i);
            Promise promise = new Promise();
            promise.setCreator(creator);
            promise.setTitle("promise-" + i);
            promise.setDate("2099-01-01");
            promise.setTime("12:00");
            em.persist(promise);
            Participant participant = new Participant();
            participant.setPromise(promise);
            participant.setHost(creator);
            participant.setGuest(guest);
            participant.setStatus(ParticipantStatus.ACCEPTED);
            if (i == count) participant.setStatus(ParticipantStatus.PENDING);
            if (i == count + 1) participant.setStatus(ParticipantStatus.DECLINED);
            if (i == count + 2) participant.setStatus(ParticipantStatus.CANCELLED);
            em.persist(participant);
        }
        em.flush();
        em.clear();
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = service.getPromiseList("guest");
        assertEquals(2, statistics.getPrepareStatementCount());
        assertEquals(count, result.size());
        for (var dto : result) {
            int index = Integer.parseInt(dto.getTitle().substring("promise-".length()));
            assertTrue(index < count);
            assertEquals("creator-" + index, dto.getCreatorUsername());
            assertEquals("2099-01-01T12:00", dto.getParticipationDeadline().toString());
            assertTrue(dto.isParticipationOpen());
        }
    }

    private SiteUser user(String name) {
        SiteUser user = new SiteUser();
        user.setUsername(name);
        user.setNickname(name);
        user.setEmail(name + "@example.test");
        user.setPassword("unused-test-password");
        em.persist(user);
        return user;
    }
}
