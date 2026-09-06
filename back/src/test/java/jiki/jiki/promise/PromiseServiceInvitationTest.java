package jiki.jiki.promise;

import jiki.jiki.config.ConflictException;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import java.time.Clock;
import java.time.LocalDateTime;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromiseServiceInvitationTest {

    @Mock
    private PromiseRepository promiseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @InjectMocks
    private PromiseService promiseService;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @Test
    void rejectsInvitationToSelf() {
        Promise promise = promiseCreatedBy("host");
        ParticipantRequestDto request = ParticipantRequestDto.builder()
                .promiseId(1L)
                .guestUsername("host")
                .build();
        when(promiseRepository.findForUpdateById(1L)).thenReturn(Optional.of(promise));
        when(userRepository.findByUsername("host")).thenReturn(Optional.of(promise.getCreator()));

        assertThrows(IllegalArgumentException.class,
                () -> promiseService.inviteParticipant("host", request));

        verify(participantRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicatePendingInvitation() {
        Promise promise = promiseCreatedBy("host");
        SiteUser guest = user("guest");
        Participant participant = new Participant();
        participant.setStatus(ParticipantStatus.PENDING);
        ParticipantRequestDto request = ParticipantRequestDto.builder()
                .promiseId(1L)
                .guestUsername("guest")
                .build();
        when(promiseRepository.findForUpdateById(1L)).thenReturn(Optional.of(promise));
        when(userRepository.findByUsername("host")).thenReturn(Optional.of(promise.getCreator()));
        when(userRepository.findByUsername("guest")).thenReturn(Optional.of(guest));
        when(participantRepository.findByPromiseIdAndGuestUsername(1L, "guest"))
                .thenReturn(Optional.of(participant));

        assertThrows(ConflictException.class,
                () -> promiseService.inviteParticipant("host", request));

        verify(participantRepository, never()).save(participant);
    }

    private Promise promiseCreatedBy(String username) {
        Promise promise = new Promise();
        promise.setId(1L);
        promise.setCreator(user(username));
        promise.setParticipationDeadline(LocalDateTime.now().plusDays(1));
        return promise;
    }

    private SiteUser user(String username) {
        SiteUser user = new SiteUser();
        user.setUsername(username);
        return user;
    }
}
