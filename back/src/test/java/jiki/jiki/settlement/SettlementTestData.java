package jiki.jiki.settlement;

import jakarta.persistence.EntityManager;
import jiki.jiki.promise.*;
import jiki.jiki.user.SiteUser;
import java.util.*;

/** Creates only synthetic data in the test's transaction. */
class SettlementTestData {
    final EntityManager em;
    SettlementTestData(EntityManager em) { this.em = em; }

    SiteUser user(String name) {
        SiteUser u = new SiteUser();
        u.setUsername(name); u.setNickname(name); u.setEmail(name + "@example.test");
        u.setPassword("not-a-login-password"); u.setMoney(10_000);
        em.persist(u);
        return u;
    }

    Promise promise(String title, List<SiteUser> late, List<SiteUser> onTime, int penalty) {
        Promise p = new Promise();
        p.setTitle(title); p.setDate("2020-01-01"); p.setTime("12:00"); p.setPenalty(penalty);
        p.setCreator(onTime.isEmpty() ? late.get(0) : onTime.get(0));
        em.persist(p);
        for (SiteUser u : late) participant(p, u, false);
        for (SiteUser u : onTime) participant(p, u, true);
        return p;
    }

    void participant(Promise p, SiteUser u, boolean arrival) {
        Participant participant = new Participant();
        participant.setPromise(p); participant.setGuest(u); participant.setHost(p.getCreator());
        participant.setStatus(ParticipantStatus.ACCEPTED); participant.setArrival(arrival);
        em.persist(participant); p.getParticipants().add(participant);
    }
}
