package jiki.jiki.payment;

import jiki.jiki.user.MoneyRecord;
import jiki.jiki.user.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoneyRecordRepository extends JpaRepository<MoneyRecord, Long> {
    List<MoneyRecord> findByUser(SiteUser user);
}