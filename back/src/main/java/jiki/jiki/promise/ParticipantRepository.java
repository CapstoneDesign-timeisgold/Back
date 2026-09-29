package jiki.jiki.promise;

import jiki.jiki.user.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    // 잠금을 얻기 전에 참여 엔티티를 읽어 오래된 상태를 캐시하지 않는다.
    @Query("select p.promise.id from Participant p where p.id = :id")
    Optional<Long> findPromiseIdByParticipantId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participant p where p.id = :id")
    Optional<Participant> findForUpdateById(@Param("id") Long id);
    List<Participant> findByGuest(SiteUser guest);

    // List DTOs need the promise and its creator; fetch both to avoid per-row SELECTs.
    @Query("select p from Participant p " +
            "join fetch p.promise promise " +
            "join fetch promise.creator " +
            "where p.guest = :guest and p.status = :status")
    List<Participant> findListWithPromiseAndCreator(
            @Param("guest") SiteUser guest, @Param("status") ParticipantStatus status);

    Set<Participant> findByGuestAndStatus(SiteUser guest, ParticipantStatus status);
    Optional<Participant> findByPromiseIdAndGuestUsername(Long promiseId, String guestUsername);
}
