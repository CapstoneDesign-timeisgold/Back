package jiki.jiki.repository;

import jiki.jiki.domain.Friend;
import jiki.jiki.domain.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendRepository extends JpaRepository<Friend, Long> {
    List<Friend> findByUser2UsernameAndStatus(String username, FriendStatus status);

    List<Friend> findByUser1UsernameOrUser2UsernameAndStatus(String username1, String username2, FriendStatus status);

}
