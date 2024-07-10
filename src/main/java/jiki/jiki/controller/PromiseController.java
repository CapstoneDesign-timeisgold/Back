package jiki.jiki.controller;

import jiki.jiki.dto.*;
import jiki.jiki.service.PromiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class PromiseController {

    private final PromiseService promiseService;

    @PostMapping("/promise")
    public ResponseEntity<PromiseDetailDto> createPromise(@RequestBody PromiseCreateDto promiseCreateDto) {
        PromiseDetailDto promise = promiseService.createPromise(promiseCreateDto);
        return ResponseEntity.ok(promise);
    }

    @PostMapping("/promise/invitation")
    public ResponseEntity<String> inviteFriends(@RequestHeader("username") String hostUsername, @RequestBody ParticipantRequestDto participantRequestDto) {
        promiseService.inviteParticipant(hostUsername, participantRequestDto);
        return ResponseEntity.ok("Friends invited to the promise");
    }

    // 약속 초대 요청 목록 조회
    @GetMapping("/promise/invitation/{username}")
    public ResponseEntity<Set<ParticipantRequestListDto>> getPromiseInvitations(@PathVariable("username") String guestUsername) {
        Set<ParticipantRequestListDto> invitations = promiseService.getPromiseInvitations(guestUsername);
        return ResponseEntity.ok(invitations);
    }

    @PostMapping("/promise/accept/{participantId}")
    public ResponseEntity<PromiseDetailDto> acceptPromiseInvitation(@RequestHeader("username") String guestUsername, @PathVariable("participantId") Long participantId) {
        PromiseDetailDto promiseDetail = promiseService.acceptPromiseInvitation(guestUsername, participantId);
        return ResponseEntity.ok(promiseDetail);
    }

    @PostMapping("/promise/decline/{participantId}")
    public ResponseEntity<String> declinePromiseInvitation(@RequestHeader("username") String guestUsername, @PathVariable("participantId") Long participantId) {
        promiseService.declinePromiseInvitation(guestUsername, participantId);
        return ResponseEntity.ok("Promise invitation declined");
    }

    // 약속 리스트
    @GetMapping("/promise")
    public ResponseEntity<List<PromiseListDto>> getPromiseList(@RequestHeader("username") String guestUsername) {
        List<PromiseListDto> promises = promiseService.getPromiseList(guestUsername);
        return ResponseEntity.ok(promises);
    }

    // 약속 상세보기
    @GetMapping("/promise/{promiseId}")
    public ResponseEntity<PromiseDetailDto> getPromiseDetail(@PathVariable("promiseId") Long promiseId, @RequestHeader("username") String guestUsername) {
        PromiseDetailDto promiseDetail = promiseService.getPromiseDetail(promiseId, guestUsername);
        return ResponseEntity.ok(promiseDetail);
    }

    // 약속 삭제
    @DeleteMapping("/promise/{promiseId}")
    public ResponseEntity<String> deletePromise(@PathVariable("promiseId") Long promiseId, @RequestHeader("username") String hostUsername) {
        promiseService.deletePromise(promiseId, hostUsername);
        return ResponseEntity.ok("Promise deleted successfully");
    }

    // 약속에 늦었는지 여부 업데이트
    @PostMapping("/promise/update-late-status")
    public ResponseEntity<Void> updateLateStatus(@RequestHeader("username") String username, @RequestBody UpdateLateStatusDto updateLateStatusDto) {
        promiseService.updateLateStatus(updateLateStatusDto, username);
        return ResponseEntity.ok().build();
    }
}