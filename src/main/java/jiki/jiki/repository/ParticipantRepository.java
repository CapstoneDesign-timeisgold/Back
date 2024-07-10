package jiki.jiki.repository;

import jiki.jiki.domain.Participant;
import jiki.jiki.domain.ParticipantStatus;
import jiki.jiki.domain.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findByGuest(SiteUser guest);
    Set<Participant> findByGuestAndStatus(SiteUser guest, ParticipantStatus status);
    Optional<Participant> findByPromiseIdAndGuestUsername(Long promiseId, String guestUsername);
}