package jiki.jiki.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jiki.jiki.domain.*;
import jiki.jiki.dto.*;
import jiki.jiki.repository.ParticipantRepository;
import jiki.jiki.repository.PromiseRepository;
import jiki.jiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromiseService {

    private final PromiseRepository promiseRepository;
    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;

    //약속 생성
    @Transactional
    public PromiseDetailDto createPromise(PromiseCreateDto promiseCreateDto) {
        SiteUser host = userRepository.findByUsername(promiseCreateDto.getCreatorUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Promise promise = new Promise();
        promise.setTitle(promiseCreateDto.getTitle());
        promise.setDate(promiseCreateDto.getDate());
        promise.setTime(promiseCreateDto.getTime());
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
                .latitude(promise.getLatitude())
                .longitude(promise.getLongitude())
                .penalty(promise.getPenalty())
                .participantUsernames(Set.of(host.getUsername()))
                .build();

        return dto;
    }

    //약속 목록
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
                .map(participant -> participant.getGuest().getUsername())
                .collect(Collectors.toSet());

        Set<Long> participantIds = promise.getParticipants().stream()
                .map(Participant::getId)
                .collect(Collectors.toSet());

        return PromiseDetailDto.builder()
                .promiseId(promiseId)
                .title(promise.getTitle())
                .date(promise.getDate())
                .time(promise.getTime())
                .latitude(promise.getLatitude())
                .longitude(promise.getLongitude())
                .penalty(promise.getPenalty())
                .participantUsernames(participantUsernames)
                .participantIds(participantIds)
                .build();
    }

    //약속 초대
    public void inviteParticipant(String hostUsername, ParticipantRequestDto participantRequestDto) {
        Promise promise = promiseRepository.findById(participantRequestDto.getPromiseId())
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + participantRequestDto.getPromiseId()));

        SiteUser host = userRepository.findByUsername(hostUsername)
                .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + hostUsername));

        if (!promise.getCreator().equals(host)) {
            throw new IllegalArgumentException("User not authorized to invite friends to this promise");
        }

        SiteUser guest = userRepository.findByUsername(participantRequestDto.getGuestUsername())
                .orElseThrow(() -> new EntityNotFoundException("Invalid guest username: " + participantRequestDto.getGuestUsername()));

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
                .map(participant -> ParticipantRequestListDto.builder()
                        .promiseId(participant.getPromise().getId())
                        .hostUsername(participant.getHost().getUsername())
                        .guestUsername(participant.getGuest().getUsername())
                        .participantId(participant.getId())
                        .title(participant.getPromise().getTitle())
                        .build()
                )
                .collect(Collectors.toSet());
    }

    //약속 수락
    public PromiseDetailDto acceptPromiseInvitation(String guestUsername, Long participantId) {
        Participant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid participant ID: " + participantId));

        if (!participant.getGuest().getUsername().equals(guestUsername)) {
            throw new IllegalArgumentException("User not authorized to accept this promise invitation");
        }

        if (participant.getStatus() == ParticipantStatus.PENDING) {
            participant.setStatus(ParticipantStatus.ACCEPTED);
            participantRepository.save(participant);
        } else {
            throw new IllegalStateException("Cannot accept promise invitation: Participant status is not pending");
        }

        return getPromiseDetail(participant.getPromise().getId(), guestUsername);
    }

    //약속 거절
    public void declinePromiseInvitation(String guestUsername, Long participantId) {
        Participant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid participant ID: " + participantId));

        if (!participant.getGuest().getUsername().equals(guestUsername)) {
            throw new IllegalArgumentException("User not authorized to decline this promise invitation");
        }

        if (participant.getStatus() == ParticipantStatus.PENDING) {
            participant.setStatus(ParticipantStatus.DECLINED);
            participantRepository.save(participant);
        } else {
            throw new IllegalStateException("Cannot decline promise invitation: Participant status is not pending");
        }
    }

    //약속 삭제
    @Transactional
    public void deletePromise(Long promiseId, String hostUsername) {
        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + promiseId));

        SiteUser host = userRepository.findByUsername(hostUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!promise.getCreator().equals(host)) {
            throw new IllegalArgumentException("User not authorized to delete this promise");
        }

        promiseRepository.delete(promise);
    }

    // 약속에 늦었는지 여부를 업데이트
    @Transactional
    public void updateLateStatus(UpdateLateStatusDto updateLateStatusDto, String username) {
        SiteUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + username));

        Participant participant = participantRepository.findByPromiseIdAndGuestUsername(updateLateStatusDto.getPromiseId(), username)
                .orElseThrow(() -> new EntityNotFoundException("Invalid participant or promise ID: " + updateLateStatusDto.getPromiseId()));

        // 주최자 혹은 해당 참여자 본인만 지각 상태를 업데이트할 수 있도록 허용
        if (!participant.getHost().equals(user) && !participant.getPromise().getCreator().equals(user) && !participant.getGuest().equals(user)) {
            throw new IllegalArgumentException("User not authorized to update late status for this participant");
        }

        // 지각 상태가 이미 true인 경우 업데이트를 허용하지 않음
        if (participant.isArrival()) {
            throw new IllegalStateException("Cannot update late status once it has been set to true");
        }

        participant.setArrival(updateLateStatusDto.isArrival());
        participantRepository.save(participant);
    }
}