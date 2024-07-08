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
        hostParticipant.setLate(false);
        hostParticipant.setStatus(ParticipantStatus.ACCEPTED);
        participantRepository.save(hostParticipant);

        promise.getParticipants().add(hostParticipant);

        // DTO 변환
        PromiseDetailDto dto = new PromiseDetailDto();
        dto.setPromiseId(promise.getId());
        dto.setTitle(promise.getTitle());
        dto.setDate(promise.getDate());
        dto.setTime(promise.getTime());
        dto.setLatitude(promise.getLatitude());
        dto.setLongitude(promise.getLongitude());
        dto.setPenalty(promise.getPenalty());
        dto.setParticipantUsernames(Set.of(host.getUsername()));

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
                    PromiseListDto dto = new PromiseListDto();
                    dto.setTitle(promise.getTitle());
                    dto.setDate(promise.getDate());
                    dto.setTime(promise.getTime());
                    dto.setPromiseId(promise.getId());
                    dto.setCreatorUsername(promise.getCreator().getUsername());
                    return dto;
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

        PromiseDetailDto dto = new PromiseDetailDto();
        dto.setPromiseId(promiseId);
        dto.setTitle(promise.getTitle());
        dto.setDate(promise.getDate());
        dto.setTime(promise.getTime());
        dto.setLongitude(promise.getLongitude());
        dto.setLatitude(promise.getLatitude());
        dto.setPenalty(promise.getPenalty());

        Set<String> participantUsernames = promise.getParticipants().stream()
                .map(participant -> participant.getGuest().getUsername())
                .collect(Collectors.toSet());

        Set<Long> participantIds = promise.getParticipants().stream()
                .map(Participant::getId)
                .collect(Collectors.toSet());

        dto.setParticipantUsernames(participantUsernames);
        dto.setParticipantIds(participantIds); // 수정된 부분
        return dto;
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
        participant.setLate(false);
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
                .map(participant -> {
                    ParticipantRequestListDto dto = new ParticipantRequestListDto();
                    dto.setPromiseId(participant.getPromise().getId());
                    dto.setHostUsername(participant.getHost().getUsername());
                    dto.setGuestUsername(participant.getGuest().getUsername());
                    dto.setParticipantId(participant.getId());
                    return dto;
                })
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
    public void updateLateStatus(UpdateLateStatusDto updateLateStatusDto) {
        Participant participant = participantRepository.findById(updateLateStatusDto.getParticipantId())
                .orElseThrow(() -> new EntityNotFoundException("Invalid participant ID: " + updateLateStatusDto.getParticipantId()));

        participant.setLate(updateLateStatusDto.isLate());
        participantRepository.save(participant);
    }
}