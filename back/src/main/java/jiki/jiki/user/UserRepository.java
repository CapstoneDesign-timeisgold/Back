package jiki.jiki.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<SiteUser, Long> {
    // Scalar locking read bypasses potentially stale managed SiteUser.money state.
    @Query(value = "select money from site_user where id = :id for update", nativeQuery = true)
    Optional<Integer> lockBalanceById(@Param("id") Long id);

    // Only settlement writes money. Caller holds the account lock until transaction commit.
    @Modifying
    @Query(value = "update site_user set money = :balance where id = :id", nativeQuery = true)
    int updateLockedBalance(@Param("id") Long id, @Param("balance") int balance);
    Optional<SiteUser> findByUsername(String username); //사용자 아이디로 사용자 찾기
    Optional<SiteUser> findByNickname(String nickname); // 닉네임으로 사용자 찾기
}

