package jiki.jiki.promise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@Repository
public interface PromiseRepository extends JpaRepository<Promise, Long> {
    // 모든 참여 변경과 정산은 같은 약속 행을 먼저 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promise p where p.id = :id")
    Optional<Promise> findForUpdateById(@Param("id") Long id);
}
