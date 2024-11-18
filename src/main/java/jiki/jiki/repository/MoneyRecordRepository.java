package jiki.jiki.repository;

import jiki.jiki.domain.MoneyRecord;
import jiki.jiki.domain.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoneyRecordRepository extends JpaRepository<MoneyRecord, Long> {
    List<MoneyRecord> findByUser(SiteUser user);
}