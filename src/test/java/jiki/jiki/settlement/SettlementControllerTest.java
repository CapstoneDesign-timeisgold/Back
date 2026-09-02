package jiki.jiki.settlement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementControllerTest {

    @Mock
    private SettlementService settlementService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SettlementController settlementController;

    @Test
    void getsResultForAuthenticatedUser() {
        when(authentication.getName()).thenReturn("participant");

        settlementController.getPromiseResultDetails(authentication, 1L);

        verify(settlementService).getPromiseResultDetails(1L, "participant");
    }

    @Test
    void settlesRewardsAsAuthenticatedUser() {
        RewardDto rewardDto = new RewardDto();
        rewardDto.setPromiseId(1L);
        when(authentication.getName()).thenReturn("creator");

        settlementController.decideRewards(authentication, rewardDto);

        verify(settlementService).decideRewards("creator", rewardDto);
    }
}
