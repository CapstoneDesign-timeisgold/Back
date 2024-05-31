package jiki.jiki.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jiki.jiki.domain.Participant;
import jiki.jiki.domain.ParticipantStatus;
import jiki.jiki.domain.Promise;
import jiki.jiki.domain.SiteUser;
import jiki.jiki.dto.*;
import jiki.jiki.repository.ParticipantRepository;
import jiki.jiki.repository.PromiseRepository;
import jiki.jiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class PromiseService {

    private final PromiseRepository promiseRepository;
    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;

    //약속 생성
    @Transactional
    public Promise createPromise(PromiseCreateDto promiseCreateDto) {
        SiteUser creator = userRepository.findByUsername(promiseCreateDto.getCreatorUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Promise promise = new Promise();
        promise.setTitle(promiseCreateDto.getTitle());
        promise.setDate(promiseCreateDto.getDate());
        promise.setTime(promiseCreateDto.getTime());
        promise.setLocation(promiseCreateDto.getLocation());
        promise.setPenalty(promiseCreateDto.getPenalty());

        promise = promiseRepository.save(promise);

        // 생성자를 참가자로 추가
        Participant creatorParticipant = new Participant();
        creatorParticipant.setPromise(promise);
        creatorParticipant.setUser(creator);
        creatorParticipant.setLate(false);
        creatorParticipant.setStatus(ParticipantStatus.ACCEPTED);
        participantRepository.save(creatorParticipant);

        return promise;
    }

    //약속 목록
    public List<PromiseListDto> getPromiseList(String username) {
        SiteUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<Promise> promises = promiseRepository.findByParticipants_User(user);

        return promises.stream().map(promise -> {
            PromiseListDto dto = new PromiseListDto();
            dto.setTitle(promise.getTitle());
            dto.setDate(promise.getDate());
            dto.setTime(promise.getTime());
            dto.setPromiseId(promise.getId()); // promiseId 값 설정
            return dto;
        }).collect(Collectors.toList());
    }

    // 약속 세부사항
    public PromiseDetailDto getPromiseDetail(Long promiseId, String username) {
        SiteUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + promiseId));

        // Check if user is a participant of the promise
        boolean isParticipant = promise.getParticipants().stream()
                .anyMatch(participant -> participant.getUser().equals(user));

        if (!isParticipant) {
            throw new IllegalArgumentException("User not authorized to view this promise detail");
        }

        PromiseDetailDto dto = new PromiseDetailDto();
        dto.setPromiseId(promiseId); // promiseId 설정
        dto.setTitle(promise.getTitle());
        dto.setDate(promise.getDate());
        dto.setTime(promise.getTime());
        dto.setLocation(promise.getLocation());
        dto.setPenalty(promise.getPenalty());

        Set<String> participantUsernames = promise.getParticipants().stream()
                .map(participant -> participant.getUser().getUsername())
                .collect(Collectors.toSet());

        dto.setParticipantUsernames(participantUsernames);
        return dto;
    }

    //약속초대
    public void inviteParticipant(String username, ParticipantDto participantDto) {
        Promise promise = promiseRepository.findById(participantDto.getPromiseId())
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + participantDto.getPromiseId()));

        // 인증된 사용자가 promise의 생성자인지 확인
        Participant creatorParticipant = participantRepository.findByPromiseAndUser(promise, userRepository.findByUsername(username)
                        .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + username)))
                .orElseThrow(() -> new IllegalArgumentException("User not authorized to invite friends to this promise"));

        Set<Participant> participants = new HashSet<>();

        for (String friendUsername : participantDto.getFriendUsernames()) {
            SiteUser friend = userRepository.findByUsername(friendUsername)
                    .orElseThrow(() -> new EntityNotFoundException("Invalid friend username: " + friendUsername));

            Participant participant = new Participant();
            participant.setPromise(promise);
            participant.setUser(friend);
            participant.setLate(false);
            participants.add(participant);
        }

        participantRepository.saveAll(participants);
    }

    // 약속 수락 로직
    public void acceptPromiseInvitation(String username, Long participantId) {
        Participant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid participant ID: " + participantId));

        // 인증된 사용자가 해당 약속의 초대를 수락할 권한이 있는지 확인
        if (!participant.getUser().getUsername().equals(username)) {
            throw new IllegalArgumentException("User not authorized to accept this promise invitation");
        }

        // 참여 상태가 "대기"인지 확인
        if (participant.getStatus() == ParticipantStatus.PENDING) {
            participant.setStatus(ParticipantStatus.ACCEPTED);
            participantRepository.save(participant);
        } else {
            throw new IllegalStateException("Cannot accept promise invitation: Participant status is not pending");
        }
    }

    // 약속 거절 로직
    public void declinePromiseInvitation(String username, Long participantId) {
        Participant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid participant ID: " + participantId));

        // 인증된 사용자가 해당 약속의 초대를 거절할 권한이 있는지 확인
        if (!participant.getUser().getUsername().equals(username)) {
            throw new IllegalArgumentException("User not authorized to decline this promise invitation");
        }

        // 참여 상태가 "대기"인지 확인
        if (participant.getStatus() == ParticipantStatus.PENDING) {
            participant.setStatus(ParticipantStatus.DECLINED);
            participantRepository.save(participant);
        } else {
            throw new IllegalStateException("Cannot decline promise invitation: Participant status is not pending");
        }
    }


}