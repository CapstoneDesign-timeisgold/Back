package jiki.jiki.settlement;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;

/** Retry outside the transactional service proxy, including failures at commit. */
@Service
@RequiredArgsConstructor
public class SettlementRetryService {
    private static final Logger log = LoggerFactory.getLogger(SettlementRetryService.class);
    private static final int MAX_ATTEMPTS = 3;
    private final SettlementService settlements;

    @Transactional(propagation = Propagation.NEVER)
    public PromiseResultDto decideRewards(String username, RewardDto request) {
        for (int attempt = 1; ; attempt++) {
            try {
                // Every call crosses the proxy: rollback completes before retry/backoff.
                return settlements.decideRewards(username, request);
            } catch (RuntimeException failure) {
                if (!isLockConflict(failure)) throw failure;
                log.warn("Settlement lock conflict: promiseId={}, attempt={}/{}",
                        request == null ? null : request.getPromiseId(), attempt, MAX_ATTEMPTS);
                if (attempt == MAX_ATTEMPTS) throw new SettlementBusyException(failure);
                try {
                    long baseMillis = 50L * attempt;
                    Thread.sleep(ThreadLocalRandom.current().nextLong(baseMillis, baseMillis * 2 + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new SettlementBusyException(interrupted);
                }
            }
        }
    }

    private boolean isLockConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException
                    || cause instanceof jakarta.persistence.LockTimeoutException
                    || cause instanceof jakarta.persistence.PessimisticLockException) return true;
            if (cause instanceof SQLException sql) {
                for (SQLException next = sql; next != null; next = next.getNextException()) {
                    // MariaDB: lock wait timeout and deadlock. Never retry arbitrary SQL errors.
                    if (next.getErrorCode() == 1205 || next.getErrorCode() == 1213) return true;
                }
            }
        }
        return false;
    }
}
