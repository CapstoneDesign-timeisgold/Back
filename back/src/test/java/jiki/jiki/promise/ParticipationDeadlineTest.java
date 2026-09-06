package jiki.jiki.promise;

import jiki.jiki.config.ConflictException;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ParticipationDeadlineTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 12, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    private final PromiseRepository promises = mock(PromiseRepository.class);
    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PromiseService service = new PromiseService(promises, users, participants, clock);

    enum Action { INVITE, ACCEPT, DECLINE, CANCEL, DELETE, CHANGE_DEADLINE }

    @Test
    void createsSameDayPromiseAndReturnsCustomDeadline() {
        PromiseCreateDto request = request(NOW.plusMinutes(25));
        when(users.findByUsername("host")).thenReturn(Optional.of(user("host")));
        when(promises.save(any())).thenAnswer(call -> {
            Promise p = call.getArgument(0);
            p.setId(1L);
            return p;
        });
        PromiseDetailDto result = service.createPromise("host", request);
        assertEquals(NOW.plusMinutes(25), result.getParticipationDeadline());
        assertTrue(result.isParticipationOpen());
        assertTrue(result.isParticipationDeadlineEditable());
        verify(promises).save(argThat(p -> p.getParticipationDeadline().equals(NOW.plusMinutes(25))));
    }

    static Stream<LocalDateTime> invalidDeadlines() {
        return Stream.of(null, NOW.minusSeconds(1), NOW, NOW.plusMinutes(31));
    }

    @ParameterizedTest
    @MethodSource("invalidDeadlines")
    void rejectsInvalidDeadlineBeforeAccessingDatabase(LocalDateTime deadline) {
        assertThrows(IllegalArgumentException.class, () -> service.createPromise("host", request(deadline)));
        verifyNoInteractions(promises, participants, users);
    }

    @Test
    void acceptsDeadlineEqualToStartTime() {
        when(users.findByUsername("host")).thenReturn(Optional.of(user("host")));
        when(promises.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(NOW.plusMinutes(30), service.createPromise("host", request(NOW.plusMinutes(30))).getParticipationDeadline());
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void allowsChangesOneSecondBeforeDeadline(Action action) {
        Promise promise = promise(NOW.plusSeconds(1));
        Participant participant = wire(promise, action == Action.CANCEL ? ParticipantStatus.ACCEPTED : ParticipantStatus.PENDING);
        assertDoesNotThrow(() -> act(action));
        if (action == Action.ACCEPT) {
            assertEquals(ParticipantStatus.ACCEPTED, participant.getStatus());
            assertTrue(promise.isParticipationDeadlineLocked());
        }
        if (action == Action.CANCEL) {
            assertEquals(ParticipantStatus.CANCELLED, participant.getStatus());
            assertTrue(promise.isParticipationDeadlineLocked());
            assertFalse(participant.isArrival());
        }
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void rejectsChangesExactlyAtDeadline(Action action) {
        Promise promise = promise(NOW);
        ParticipantStatus status = action == Action.CANCEL ? ParticipantStatus.ACCEPTED : ParticipantStatus.PENDING;
        Participant participant = wire(promise, status);
        assertThrows(ConflictException.class, () -> act(action));
        assertEquals(status, participant.getStatus());
        assertEquals(NOW, promise.getParticipationDeadline());
        verify(participants, never()).save(any());
        verify(promises, never()).delete(any());
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void rejectsChangesAfterDeadline(Action action) {
        wire(promise(NOW.minusSeconds(1)), ParticipantStatus.ACCEPTED);
        assertThrows(ConflictException.class, () -> act(action));
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void rejectsChangesToSettledPromiseEvenWithFutureDeadline(Action action) {
        Promise promise = promise(NOW.plusMinutes(10));
        promise.setSettled(true);
        wire(promise, ParticipantStatus.ACCEPTED);
        assertThrows(ConflictException.class, () -> act(action));
    }

    @Test
    void deadlineRemainsLockedAfterAcceptedGuestCancels() {
        Promise promise = promise(NOW.plusMinutes(10));
        wire(promise, ParticipantStatus.ACCEPTED);
        service.cancelParticipation("guest", 2L);
        assertThrows(ConflictException.class, () -> act(Action.CHANGE_DEADLINE));
        assertEquals(NOW.plusMinutes(10), promise.getParticipationDeadline());
    }

    @Test
    void legacyAcceptedGuestAlsoLocksDeadline() {
        wire(promise(NOW.plusMinutes(10)), ParticipantStatus.ACCEPTED);
        assertThrows(ConflictException.class, () -> act(Action.CHANGE_DEADLINE));
    }

    @Test
    void onlyGuestCanCancelAndOnlyCreatorCanChangeDeadline() {
        wire(promise(NOW.plusMinutes(10)), ParticipantStatus.ACCEPTED);
        assertThrows(AccessDeniedException.class, () -> service.cancelParticipation("other", 2L));
        assertThrows(AccessDeniedException.class, () -> service.updateParticipationDeadline("guest", 1L, deadlineRequest()));
    }

    @Test
    void creatorCannotLeaveOwnPromise() {
        Promise promise = promise(NOW.plusMinutes(10));
        Participant participant = wire(promise, ParticipantStatus.ACCEPTED);
        participant.setGuest(promise.getCreator());
        assertThrows(ConflictException.class, () -> service.cancelParticipation("host", 2L));
    }

    @Test
    void cancelledGuestCanBeReinvitedWithoutUnlockingDeadline() {
        Promise promise = promise(NOW.plusMinutes(10));
        Participant participant = wire(promise, ParticipantStatus.ACCEPTED);
        service.cancelParticipation("guest", 2L);
        when(participants.findByPromiseIdAndGuestUsername(1L, "guest")).thenReturn(Optional.of(participant));
        act(Action.INVITE);
        assertEquals(ParticipantStatus.PENDING, participant.getStatus());
        assertTrue(promise.isParticipationDeadlineLocked());
    }

    @Test
    void pendingInvitationsDisappearAtDeadlineAndLegacyDeadlineUsesStart() {
        Promise open = promise(null);
        Promise closed = promise(NOW);
        Participant first = participant(open, ParticipantStatus.PENDING);
        Participant second = participant(closed, ParticipantStatus.PENDING);
        second.setId(3L);
        SiteUser guest = user("guest");
        when(users.findByUsername("guest")).thenReturn(Optional.of(guest));
        when(participants.findByGuestAndStatus(guest, ParticipantStatus.PENDING)).thenReturn(Set.of(first, second));
        Set<ParticipantRequestListDto> invitations = service.getPromiseInvitations("guest");
        assertEquals(1, invitations.size());
        assertEquals(NOW.plusMinutes(30), invitations.iterator().next().getParticipationDeadline());
    }

    @Test
    void keepsManualArrivalButPreventsChangesAfterSettlement() {
        Promise promise = promise(NOW.minusMinutes(1));
        Participant participant = wire(promise, ParticipantStatus.ACCEPTED);
        when(participants.findByPromiseIdAndGuestUsername(1L, "guest")).thenReturn(Optional.of(participant));
        UpdateLateStatusDto arrival = UpdateLateStatusDto.builder().promiseId(1L).arrival(true).build();
        service.updateLateStatus(arrival, "guest");
        assertTrue(participant.isArrival());
        participant.setArrival(false);
        promise.setSettled(true);
        assertThrows(ConflictException.class, () -> service.updateLateStatus(arrival, "guest"));
        assertFalse(participant.isArrival());
    }

    @ParameterizedTest
    @EnumSource(value = ParticipantStatus.class, names = {"PENDING", "DECLINED", "CANCELLED"})
    void nonParticipantsCannotConfirmArrival(ParticipantStatus status) {
        Participant participant = wire(promise(NOW.plusMinutes(10)), status);
        when(participants.findByPromiseIdAndGuestUsername(1L, "guest")).thenReturn(Optional.of(participant));
        assertThrows(ConflictException.class, () -> service.updateLateStatus(
                UpdateLateStatusDto.builder().promiseId(1L).arrival(true).build(), "guest"));
    }

    private void act(Action action) {
        switch (action) {
            case INVITE -> service.inviteParticipant("host", ParticipantRequestDto.builder().promiseId(1L).guestUsername("guest").build());
            case ACCEPT -> service.acceptPromiseInvitation("guest", 2L);
            case DECLINE -> service.declinePromiseInvitation("guest", 2L);
            case CANCEL -> service.cancelParticipation("guest", 2L);
            case DELETE -> service.deletePromise(1L, "host");
            case CHANGE_DEADLINE -> service.updateParticipationDeadline("host", 1L, deadlineRequest());
        }
    }

    private ParticipationDeadlineDto deadlineRequest() {
        ParticipationDeadlineDto dto = new ParticipationDeadlineDto();
        dto.setParticipationDeadline(NOW.plusMinutes(15));
        return dto;
    }

    private Participant wire(Promise promise, ParticipantStatus status) {
        Participant participant = participant(promise, status);
        promise.getParticipants().add(participant);
        when(promises.findForUpdateById(1L)).thenReturn(Optional.of(promise));
        when(promises.findById(1L)).thenReturn(Optional.of(promise));
        when(participants.findPromiseIdByParticipantId(2L)).thenReturn(Optional.of(1L));
        when(participants.findForUpdateById(2L)).thenReturn(Optional.of(participant));
        when(users.findByUsername("host")).thenReturn(Optional.of(promise.getCreator()));
        when(users.findByUsername("guest")).thenReturn(Optional.of(participant.getGuest()));
        return participant;
    }

    private Participant participant(Promise promise, ParticipantStatus status) {
        Participant participant = new Participant();
        participant.setId(2L);
        participant.setPromise(promise);
        participant.setHost(promise.getCreator());
        participant.setGuest(user("guest"));
        participant.setStatus(status);
        return participant;
    }

    private Promise promise(LocalDateTime deadline) {
        Promise promise = new Promise();
        promise.setId(1L);
        promise.setCreator(user("host"));
        promise.setDate("2026-09-06");
        promise.setTime("12:30");
        promise.setParticipationDeadline(deadline);
        return promise;
    }

    private SiteUser user(String username) {
        SiteUser user = new SiteUser();
        user.setUsername(username);
        return user;
    }

    private PromiseCreateDto request(LocalDateTime deadline) {
        return PromiseCreateDto.builder().date("2026-09-06").time("12:30")
                .participationDeadline(deadline).title("즉석 점심 약속").penalty(1000)
                .latitude(37.5665).longitude(126.9780).build();
    }
}
