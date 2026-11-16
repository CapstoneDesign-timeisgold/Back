package jiki.jiki.settlement;

import jiki.jiki.config.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementRetryServiceTest {
    @Mock SettlementService settlements;
    @InjectMocks SettlementRetryService retry;
    private final RewardDto request = new RewardDto(1L);

    @Test void retriesTranslatedLockFailuresThenReturnsResult() {
        var result = new PromiseResultDto();
        when(settlements.decideRewards("host", request))
                .thenThrow(new CannotAcquireLockException("lock"))
                .thenReturn(result);
        assertSame(result, retry.decideRewards("host", request));
        verify(settlements, times(2)).decideRewards("host", request);
    }

    @Test void recognizesWrappedMariaDbDeadlockAndBoundsAttempts() {
        var failure = new RuntimeException(new SQLException("private SQL", "40001", 1213));
        when(settlements.decideRewards("host", request)).thenThrow(failure);
        assertSame(failure, assertThrows(SettlementBusyException.class,
                () -> retry.decideRewards("host", request)).getCause());
        verify(settlements, times(3)).decideRewards("host", request);
    }

    @Test void recognizesMariaDbLockTimeoutInNextExceptionChain() {
        var sql = new SQLException("batch");
        sql.setNextException(new SQLException("timeout", "HY000", 1205));
        when(settlements.decideRewards("host", request))
                .thenThrow(new RuntimeException(sql)).thenReturn(new PromiseResultDto());
        retry.decideRewards("host", request);
        verify(settlements, times(2)).decideRewards("host", request);
    }

    @Test void neverRetriesBusinessAuthorizationIntegrityOrConnectionErrors() {
        for (RuntimeException failure : new RuntimeException[]{
                new ConflictException("not ended"), new AccessDeniedException("not creator"),
                new DataIntegrityViolationException("duplicate"),
                new RuntimeException(new SQLException("connection lost", "08006", 0))}) {
            reset(settlements);
            when(settlements.decideRewards("host", request)).thenThrow(failure);
            assertSame(failure, assertThrows(RuntimeException.class,
                    () -> retry.decideRewards("host", request)));
            verify(settlements).decideRewards("host", request);
        }
    }

    @Test void interruptionStopsRetriesAndPreservesInterruptFlag() {
        when(settlements.decideRewards("host", request)).thenThrow(new CannotAcquireLockException("lock"));
        Thread.currentThread().interrupt();
        try {
            assertThrows(SettlementBusyException.class, () -> retry.decideRewards("host", request));
            assertTrue(Thread.currentThread().isInterrupted());
            verify(settlements).decideRewards("host", request);
        } finally {
            Thread.interrupted();
        }
    }
}
