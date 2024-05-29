package jiki.jiki.repository;

import jiki.jiki.domain.Participant;
import jiki.jiki.domain.Promise;
import jiki.jiki.domain.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    Optional<Participant> findByPromiseAndUser(Promise promise, SiteUser user);
    List<Promise> findByParticipants_User(SiteUser user);
}