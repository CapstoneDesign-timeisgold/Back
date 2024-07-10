package jiki.jiki.repository;

import jiki.jiki.domain.Promise;
import jiki.jiki.domain.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromiseRepository extends JpaRepository<Promise, Long> {

}