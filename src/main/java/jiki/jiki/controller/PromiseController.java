package jiki.jiki.controller;

import jiki.jiki.domain.Promise;
import jiki.jiki.dto.ParticipantDto;
import jiki.jiki.dto.PromiseCreateDto;
import jiki.jiki.dto.PromiseDetailDto;
import jiki.jiki.dto.PromiseListDto;
import jiki.jiki.service.PromiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PromiseController {

    private final PromiseService promiseService;

    @PostMapping("/promise")
    public ResponseEntity<Promise> createPromise(@RequestBody PromiseCreateDto promiseCreateDto) {
        Promise promise = promiseService.createPromise(promiseCreateDto);
        return ResponseEntity.ok(promise);
    }

    @PostMapping("/promise/invite")
    public ResponseEntity<String> inviteFriends(@RequestHeader("username") String username, @RequestBody ParticipantDto participantDto) {
        promiseService.inviteParticipant(username, participantDto);
        return ResponseEntity.ok("Friends invited to the promise");
    }

    @PostMapping("/promise/accept/{participantId}")
    public ResponseEntity<String> acceptPromiseInvitation(@RequestHeader("username") String username, @PathVariable Long participantId) {
        promiseService.acceptPromiseInvitation(username, participantId);
        return ResponseEntity.ok("Promise invitation accepted");
    }

    @PostMapping("/promise/decline/{participantId}")
    public ResponseEntity<String> declinePromiseInvitation(@RequestHeader("username") String username, @PathVariable Long participantId) {
        promiseService.declinePromiseInvitation(username, participantId);
        return ResponseEntity.ok("Promise invitation declined");
    }

    //약속 리스트
    @GetMapping("/promise")
    public ResponseEntity<List<PromiseListDto>> getPromiseList(@RequestHeader("username") String username) {
        List<PromiseListDto> promises = promiseService.getPromiseList(username);
        return ResponseEntity.ok(promises);
    }

    //약속 상세보기
    @GetMapping("/promise/{promiseId}")
    public ResponseEntity<PromiseDetailDto> getPromiseDetail(@PathVariable("promiseId") Long promiseId, @RequestHeader("username") String username) {
        PromiseDetailDto promiseDetail = promiseService.getPromiseDetail(promiseId, username);
        return ResponseEntity.ok(promiseDetail);
    }

    //약속 삭제
    @DeleteMapping("/promise/{promiseId}")
    public ResponseEntity<String> deletePromise(@PathVariable Long promiseId, @RequestHeader("username") String username) {
        promiseService.deletePromise(promiseId, username);
        return ResponseEntity.ok("Promise deleted successfully");
    }

}