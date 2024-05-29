package jiki.jiki.repository;

import jiki.jiki.domain.Promise;
import jiki.jiki.domain.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromiseRepository extends JpaRepository<Promise, Long> {
    List<Promise> findByParticipants_User(SiteUser user);
}