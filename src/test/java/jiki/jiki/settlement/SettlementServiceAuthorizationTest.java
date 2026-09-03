package jiki.jiki.settlement;

import jiki.jiki.promise.Promise;
import jiki.jiki.promise.PromiseRepository;
import jiki.jiki.promise.Participant;
import jiki.jiki.promise.ParticipantStatus;
import jiki.jiki.config.ConflictException;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void rejectsSettlementBeforeScheduledTime() {
        Promise promise = promiseCreatedBy("creator");
        promise.setDate(LocalDate.now().plusDays(1).toString());
        promise.setTime(LocalTime.now().withSecond(0).withNano(0).toString());
        RewardDto rewardDto = new RewardDto();
        rewardDto.setPromiseId(1L);
        when(promiseRepository.findById(1L)).thenReturn(Optional.of(promise));

        assertThrows(ConflictException.class,
                () -> settlementService.decideRewards("creator", rewardDto));

        verify(promiseRepository, never()).save(promise);
    }

    @Test
    void excludesPendingAndDeclinedUsersFromSettlementResult() {
        Promise promise = promiseCreatedBy("creator");
        promise.setSettled(true);
        promise.setPenalty(1_000);
        promise.setParticipants(new HashSet<>());
        promise.getParticipants().add(participant("acceptedLate", ParticipantStatus.ACCEPTED, false));
        promise.getParticipants().add(participant("acceptedOnTime", ParticipantStatus.ACCEPTED, true));
        promise.getParticipants().add(participant("pending", ParticipantStatus.PENDING, false));
        promise.getParticipants().add(participant("declined", ParticipantStatus.DECLINED, false));
        when(promiseRepository.findById(1L)).thenReturn(Optional.of(promise));

        PromiseResultDto result = settlementService.getPromiseResultDetails(1L, "creator");

        assertEquals(1, result.getLateUsers().size());
        assertEquals("acceptedLate", result.getLateUsers().get(0).getUsername());
        assertEquals(1, result.getOnTimeUsers().size());
        assertEquals("acceptedOnTime", result.getOnTimeUsers().get(0).getUsername());
        assertEquals(1_000, result.getTotalPenalty());
    }

    private Promise promiseCreatedBy(String creatorUsername) {
        SiteUser creator = new SiteUser();
        creator.setUsername(creatorUsername);

        Promise promise = new Promise();
        promise.setCreator(creator);
        return promise;
    }

    private Participant participant(String username, ParticipantStatus status, boolean arrival) {
        SiteUser user = new SiteUser();
        user.setUsername(username);

        Participant participant = new Participant();
        participant.setGuest(user);
        participant.setStatus(status);
        participant.setArrival(arrival);
        return participant;
    }
}
