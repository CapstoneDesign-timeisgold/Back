package jiki.jiki.settlement;

import jiki.jiki.promise.Promise;
import jiki.jiki.promise.PromiseRepository;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceAuthorizationTest {

    @Mock
    private PromiseRepository promiseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MoneyRecordRepository moneyRecordRepository;

    @InjectMocks
    private SettlementService settlementService;

    @Test
    void rejectsSettlementByNonCreator() {
        Promise promise = promiseCreatedBy("creator");
        RewardDto rewardDto = new RewardDto();
        rewardDto.setPromiseId(1L);
        when(promiseRepository.findById(1L)).thenReturn(Optional.of(promise));

        assertThrows(AccessDeniedException.class,
                () -> settlementService.decideRewards("attacker", rewardDto));

        verify(promiseRepository, never()).save(promise);
    }

    @Test
    void rejectsSettlementResultForNonParticipant() {
        Promise promise = promiseCreatedBy("creator");
        promise.setSettled(true);
        when(promiseRepository.findById(1L)).thenReturn(Optional.of(promise));

        assertThrows(AccessDeniedException.class,
                () -> settlementService.getPromiseResultDetails(1L, "attacker"));
    }

    private Promise promiseCreatedBy(String creatorUsername) {
        SiteUser creator = new SiteUser();
        creator.setUsername(creatorUsername);

        Promise promise = new Promise();
        promise.setCreator(creator);
        return promise;
    }
}
