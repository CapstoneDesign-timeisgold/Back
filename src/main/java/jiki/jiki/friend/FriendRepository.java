package jiki.jiki.friend;

import jiki.jiki.user.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    // 친구 요청 목록 (PENDING) - fetch join으로 N+1 방지
    @Query("SELECT f FROM Friend f " +
           "JOIN FETCH f.user1 " +
           "JOIN FETCH f.user2 " +
           "WHERE f.user2.username = :username " +
           "AND f.status = :status")
    List<Friend> findPendingRequestsWithUsers(
            @Param("username") String username,
            @Param("status") FriendStatus status
    );

    // 친구 목록 (ACCEPTED) - OR/AND 우선순위 버그 수정 + fetch join
    @Query("SELECT f FROM Friend f " +
           "JOIN FETCH f.user1 " +
           "JOIN FETCH f.user2 " +
           "WHERE (f.user1.username = :username OR f.user2.username = :username) " +
           "AND f.status = :status")
    List<Friend> findAcceptedFriendsWithUsers(
            @Param("username") String username,
            @Param("status") FriendStatus status
    );

    @Query("SELECT COUNT(f) > 0 FROM Friend f " +
           "WHERE ((f.user1 = :user1 AND f.user2 = :user2) " +
           "OR (f.user1 = :user2 AND f.user2 = :user1)) " +
           "AND f.status IN :statuses")
    boolean existsActiveRelationship(
            @Param("user1") SiteUser user1,
            @Param("user2") SiteUser user2,
            @Param("statuses") List<FriendStatus> statuses
    );
}
