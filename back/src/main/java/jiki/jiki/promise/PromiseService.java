package jiki.jiki.promise;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jiki.jiki.config.ConflictException;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromiseService {

    private final PromiseRepository promiseRepository;
    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;
    private final Clock clock;

    //약속 생성
    @Transactional
    public PromiseDetailDto createPromise(String creatorUsername, PromiseCreateDto promiseCreateDto) {
        validatePromiseDateTime(promiseCreateDto);
        validateDeadline(promiseCreateDto.getParticipationDeadline(), LocalDateTime.of(
                LocalDate.parse(promiseCreateDto.getDate()), LocalTime.parse(promiseCreateDto.getTime())));

        SiteUser host = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Promise promise = new Promise();
        promise.setTitle(promiseCreateDto.getTitle());
        promise.setDate(promiseCreateDto.getDate());
        promise.setTime(promiseCreateDto.getTime());
        promise.setParticipationDeadline(promiseCreateDto.getParticipationDeadline());
        promise.setLatitude(promiseCreateDto.getLatitude());
        promise.setLongitude(promiseCreateDto.getLongitude());
        promise.setPenalty(promiseCreateDto.getPenalty());
        promise.setCreator(host);

        promise = promiseRepository.save(promise);

        Participant hostParticipant = new Participant();
        hostParticipant.setPromise(promise);
        hostParticipant.setGuest(host);
        hostParticipant.setHost(host);
        hostParticipant.setArrival(false);
        hostParticipant.setStatus(ParticipantStatus.ACCEPTED);
        participantRepository.save(hostParticipant);

        promise.getParticipants().add(hostParticipant);

        // DTO 변환
        PromiseDetailDto dto = PromiseDetailDto.builder()
                .promiseId(promise.getId())
                .title(promise.getTitle())
                .date(promise.getDate())
                .time(promise.getTime())
                .participationDeadline(deadline(promise))
                .participationOpen(isParticipationOpen(promise))
                .participationDeadlineEditable(isParticipationOpen(promise))
                .latitude(promise.getLatitude())
                .longitude(promise.getLongitude())
                .penalty(promise.getPenalty())
                .participantUsernames(Set.of(host.getUsername()))
                .participants(List.of(new ParticipantDetailDto(hostParticipant.getId(), host.getUsername(),
                        ParticipantStatus.ACCEPTED, false)))
                .build();

        return dto;
    }

    private void validatePromiseDateTime(PromiseCreateDto promiseCreateDto) {
        try {
            LocalDate.parse(promiseCreateDto.getDate());
            LocalTime.parse(promiseCreateDto.getTime());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("Promise date and time must be valid ISO values (yyyy-MM-dd, HH:mm)");
        }
    }

    //약속 목록
    @Transactional
    public List<PromiseListDto> getPromiseList(String guestUsername) {
        SiteUser guest = userRepository.findByUsername(guestUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<Participant> participants = participantRepository.findByGuest(guest);

        return participants.stream()
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACCEPTED)
                .map(participant -> {
                    Promise promise = participant.getPromise();
                    return PromiseListDto.builder()
                            .title(promise.getTitle())
                            .date(promise.getDate())
                            .time(promise.getTime())
                            .participationDeadline(deadline(promise))
                            .participationOpen(isParticipationOpen(promise))
                            .promiseId(promise.getId())
                            .creatorUsername(promise.getCreator().getUsername())
                            .build();
                }).collect(Collectors.toList());
    }

    //약속 상세 보기
    @Transactional
    public PromiseDetailDto getPromiseDetail(Long promiseId, String guestUsername) {
        SiteUser guest = userRepository.findByUsername(guestUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + promiseId));

        boolean isParticipant = promise.getParticipants().stream()
                .anyMatch(participant -> participant.getGuest().equals(guest));

        if (!isParticipant) {
            throw new IllegalArgumentException("User not authorized to view this promise detail");
        }

        Set<String> participantUsernames = promise.getParticipants().stream()
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACCEPTED)
                .map(participant -> participant.getGuest().getUsername())
                .collect(Collectors.toSet());

        Set<Long> participantIds = promise.getParticipants().stream()
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACCEPTED)
                .map(Participant::getId)
                .collect(Collectors.toSet());

        return PromiseDetailDto.builder()
                .promiseId(promiseId)
                .title(promise.getTitle())
                .date(promise.getDate())
                .time(promise.getTime())
                .participationDeadline(deadline(promise))
                .participationOpen(isParticipationOpen(promise))
                .participationDeadlineEditable(promise.getCreator().getUsername().equals(guestUsername)
                        && isParticipationOpen(promise) && !hasDeadlineBeenLocked(promise))
                .latitude(promise.getLatitude())
                .longitude(promise.getLongitude())
                .penalty(promise.getPenalty())
                .participantUsernames(participantUsernames)
                .participantIds(participantIds)
                .participants(promise.getParticipants().stream()
                        .map(p -> new ParticipantDetailDto(p.getId(), p.getGuest().getUsername(), p.getStatus(), p.isArrival()))
                        .collect(Collectors.toList()))
                .build();
    }

    //약속 초대
    @Transactional
    public void inviteParticipant(String hostUsername, ParticipantRequestDto participantRequestDto) {
        Promise promise = promiseRepository.findForUpdateById(participantRequestDto.getPromiseId())
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + participantRequestDto.getPromiseId()));

        SiteUser host = userRepository.findByUsername(hostUsername)
                .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + hostUsername));

        if (!promise.getCreator().equals(host)) {
            throw new IllegalArgumentException("User not authorized to invite friends to this promise");
        }

        if (hostUsername.equals(participantRequestDto.getGuestUsername())) {
            throw new IllegalArgumentException("Cannot invite yourself to a promise");
        }

        requireParticipationOpen(promise);

        SiteUser guest = userRepository.findByUsername(participantRequestDto.getGuestUsername())
                .orElseThrow(() -> new EntityNotFoundException("Invalid guest username: " + participantRequestDto.getGuestUsername()));

        Participant existingParticipant = participantRepository
                .findByPromiseIdAndGuestUsername(promise.getId(), guest.getUsername())
                .orElse(null);

        if (existingParticipant != null) {
            if (existingParticipant.getStatus() == ParticipantStatus.DECLINED
                    || existingParticipant.getStatus() == ParticipantStatus.CANCELLED) {
                existingParticipant.setStatus(ParticipantStatus.PENDING);
                existingParticipant.setArrival(false);
                existingParticipant.setHost(host);
                participantRepository.save(existingParticipant);
                return;
            }
            throw new ConflictException("User already has an active invitation or is participating in this promise");
        }

        Participant participant = new Participant();
        participant.setPromise(promise);
        participant.setGuest(guest);
        participant.setHost(host);
        participant.setArrival(false);
        participant.setStatus(ParticipantStatus.PENDING);

        participantRepository.save(participant);
        promise.getParticipants().add(participant);
    }

    //약속 초대 요청 목록
    @Transactional
    public Set<ParticipantRequestListDto> getPromiseInvitations(String guestUsername) {
        SiteUser guest = userRepository.findByUsername(guestUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return participantRepository.findByGuestAndStatus(guest, ParticipantStatus.PENDING)
                .stream()
                .filter(participant -> isParticipationOpen(participant.getPromise()))
                .map(participant -> ParticipantRequestListDto.builder()
                        .promiseId(participant.getPromise().getId())
                        .hostUsername(participant.getHost().getUsername())
                        .guestUsername(participant.getGuest().getUsername())
                        .participantId(participant.getId())
                        .title(participant.getPromise().getTitle())
                        .participationDeadline(deadline(participant.getPromise()))
                        .build()
                )
                .collect(Collectors.toSet());
    }

    //약속 수락
    @Transactional
    public PromiseDetailDto acceptPromiseInvitation(String guestUsername, Long participantId) {
        Participant participant = participantForUpdate(participantId);

        if (!participant.getGuest().getUsername().equals(guestUsername)) {
            throw new AccessDeniedException("User not authorized to accept this promise invitation");
        }

        requireParticipationOpen(participant.getPromise());

        if (participant.getStatus() == ParticipantStatus.PENDING) {
            participant.setStatus(ParticipantStatus.ACCEPTED);
            participant.getPromise().setParticipationDeadlineLocked(true);
            participantRepository.save(participant);
        } else {
            throw new ConflictException("Cannot accept promise invitation: Participant status is not pending");
        }

        return getPromiseDetail(participant.getPromise().getId(), guestUsername);
    }

    //약속 거절
    @Transactional
    public void declinePromiseInvitation(String guestUsername, Long participantId) {
        Participant participant = participantForUpdate(participantId);

        if (!participant.getGuest().getUsername().equals(guestUsername)) {
            throw new AccessDeniedException("User not authorized to decline this promise invitation");
        }

        requireParticipationOpen(participant.getPromise());

        if (participant.getStatus() == ParticipantStatus.PENDING) {
            participant.setStatus(ParticipantStatus.DECLINED);
            participantRepository.save(participant);
        } else {
            throw new ConflictException("Cannot decline promise invitation: Participant status is not pending");
        }
    }

    //약속 삭제
    @Transactional
    public void deletePromise(Long promiseId, String hostUsername) {
        Promise promise = promiseRepository.findForUpdateById(promiseId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + promiseId));

        SiteUser host = userRepository.findByUsername(hostUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!promise.getCreator().equals(host)) {
            throw new IllegalArgumentException("User not authorized to delete this promise");
        }

        requireParticipationOpen(promise);
        promiseRepository.delete(promise);
    }

    // 약속에 늦었는지 여부 업데이트
    @Transactional
    public void updateLateStatus(UpdateLateStatusDto updateLateStatusDto, String username) {
        Promise promise = promiseForUpdate(updateLateStatusDto.getPromiseId());
        SiteUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + username));

        Participant participant = participantRepository.findByPromiseIdAndGuestUsername(updateLateStatusDto.getPromiseId(), username)
                .orElseThrow(() -> new EntityNotFoundException("Invalid participant or promise ID: " + updateLateStatusDto.getPromiseId()));

        // 주최자 혹은 해당 참여자 본인만 지각 상태를 업데이트할 수 있도록 허용
        if (!participant.getHost().equals(user) && !participant.getPromise().getCreator().equals(user) && !participant.getGuest().equals(user)) {
            throw new IllegalArgumentException("User not authorized to update late status for this participant");
        }

        requireNotSettled(promise);
        if (participant.getStatus() != ParticipantStatus.ACCEPTED) {
            throw new ConflictException("Only accepted participants can confirm arrival");
        }

        // 사용자가 직접 누르는 도착 확인 방식은 유지한다.
        if (participant.isArrival()) {
            throw new ConflictException("Cannot update late status once it has been set to true");
        }

        participant.setArrival(updateLateStatusDto.isArrival());
        participantRepository.save(participant);
    }

    @Transactional
    public void cancelParticipation(String username, Long participantId) {
        Participant participant = participantForUpdate(participantId);
        if (!participant.getGuest().getUsername().equals(username)) {
            throw new AccessDeniedException("Only the participant can cancel participation");
        }
        Promise promise = participant.getPromise();
        requireParticipationOpen(promise);
        if (promise.getCreator().getUsername().equals(username)) {
            throw new ConflictException("The creator must cancel the promise instead of leaving it");
        }
        if (participant.getStatus() != ParticipantStatus.ACCEPTED) {
            throw new ConflictException("Only accepted participation can be cancelled");
        }
        // 기존 데이터도 취소 후 마감 변경이 다시 열리지 않도록 기록한다.
        promise.setParticipationDeadlineLocked(true);
        participant.setStatus(ParticipantStatus.CANCELLED);
        participant.setArrival(false);
        participantRepository.save(participant);
    }

    @Transactional
    public void updateParticipationDeadline(String username, Long promiseId, ParticipationDeadlineDto request) {
        Promise promise = promiseForUpdate(promiseId);
        if (!promise.getCreator().getUsername().equals(username)) {
            throw new AccessDeniedException("Only the creator can change the participation deadline");
        }
        requireParticipationOpen(promise);
        if (hasDeadlineBeenLocked(promise)) {
            throw new ConflictException("The participation deadline is locked after the first guest accepts");
        }
        validateDeadline(request.getParticipationDeadline(), scheduledAt(promise));
        promise.setParticipationDeadline(request.getParticipationDeadline());
    }

    private Promise promiseForUpdate(Long promiseId) {
        return promiseRepository.findForUpdateById(promiseId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + promiseId));
    }

    private Participant participantForUpdate(Long participantId) {
        Long promiseId = participantRepository.findPromiseIdByParticipantId(participantId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid participant ID: " + participantId));
        promiseForUpdate(promiseId);
        return participantRepository.findForUpdateById(participantId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid participant ID: " + participantId));
    }

    private LocalDateTime scheduledAt(Promise promise) {
        return LocalDateTime.of(LocalDate.parse(promise.getDate()), LocalTime.parse(promise.getTime()));
    }

    private LocalDateTime deadline(Promise promise) {
        return promise.getParticipationDeadline() != null
                ? promise.getParticipationDeadline() : scheduledAt(promise);
    }

    private void validateDeadline(LocalDateTime deadline, LocalDateTime scheduledAt) {
        if (deadline == null || !deadline.isAfter(LocalDateTime.now(clock)) || deadline.isAfter(scheduledAt)) {
            throw new IllegalArgumentException("Participation deadline must be after now and no later than the promise start");
        }
    }

    private boolean isParticipationOpen(Promise promise) {
        return !promise.isSettled() && LocalDateTime.now(clock).isBefore(deadline(promise));
    }

    private void requireParticipationOpen(Promise promise) {
        requireNotSettled(promise);
        if (!isParticipationOpen(promise)) {
            throw new ConflictException("Participation has closed for this promise");
        }
    }

    private void requireNotSettled(Promise promise) {
        if (promise.isSettled()) {
            throw new ConflictException("Settled promises cannot be changed");
        }
    }

    private boolean hasDeadlineBeenLocked(Promise promise) {
        return promise.isParticipationDeadlineLocked() || promise.getParticipants().stream()
                .anyMatch(p -> (p.getStatus() == ParticipantStatus.ACCEPTED || p.getStatus() == ParticipantStatus.CANCELLED)
                        && !p.getGuest().getUsername().equals(promise.getCreator().getUsername()));
    }

}
