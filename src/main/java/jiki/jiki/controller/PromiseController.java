package jiki.jiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jiki.jiki.dto.*;
import jiki.jiki.service.PromiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/promise")
@Tag(name = "Promise API", description = "약속 관련 API")
public class PromiseController {

    private final PromiseService promiseService;

    @PostMapping
    @Operation(summary = "약속 생성", description = "새로운 약속을 생성합니다.")
    public ResponseEntity<PromiseDetailDto> createPromise(@RequestBody PromiseCreateDto promiseCreateDto) {
        PromiseDetailDto promise = promiseService.createPromise(promiseCreateDto);
        return ResponseEntity.ok(promise);
    }

    @PostMapping("/invitation")
    @Operation(summary = "친구 초대", description = "약속에 친구를 초대합니다.")
    public ResponseEntity<String> inviteFriends(@RequestHeader("username") String hostUsername,
                                                @RequestBody ParticipantRequestDto participantRequestDto) {
        promiseService.inviteParticipant(hostUsername, participantRequestDto);
        return ResponseEntity.ok("Friends invited to the promise");
    }

    @GetMapping("/invitation/{username}")
    @Operation(summary = "초대 요청 조회", description = "사용자가 받은 약속 초대 목록을 조회합니다.")
    public ResponseEntity<Set<ParticipantRequestListDto>> getPromiseInvitations(@PathVariable("username") String guestUsername) {
        Set<ParticipantRequestListDto> invitations = promiseService.getPromiseInvitations(guestUsername);
        return ResponseEntity.ok(invitations);
    }

    @PostMapping("/accept/{participantId}")
    @Operation(summary = "초대 수락", description = "약속 초대를 수락합니다.")
    public ResponseEntity<PromiseDetailDto> acceptPromiseInvitation(@RequestHeader("username") String guestUsername,
                                                                    @PathVariable("participantId") Long participantId) {
        PromiseDetailDto promiseDetail = promiseService.acceptPromiseInvitation(guestUsername, participantId);
        return ResponseEntity.ok(promiseDetail);
    }

    @PostMapping("/decline/{participantId}")
    @Operation(summary = "초대 거절", description = "약속 초대를 거절합니다.")
    public ResponseEntity<String> declinePromiseInvitation(@RequestHeader("username") String guestUsername,
                                                           @PathVariable("participantId") Long participantId) {
        promiseService.declinePromiseInvitation(guestUsername, participantId);
        return ResponseEntity.ok("Promise invitation declined");
    }

    @GetMapping
    @Operation(summary = "약속 리스트 조회", description = "사용자의 약속 리스트를 조회합니다.")
    public ResponseEntity<List<PromiseListDto>> getPromiseList(@RequestHeader("username") String guestUsername) {
        List<PromiseListDto> promises = promiseService.getPromiseList(guestUsername);
        return ResponseEntity.ok(promises);
    }

    @GetMapping("/{promiseId}")
    @Operation(summary = "약속 상세 조회", description = "약속의 상세 정보를 조회합니다.")
    public ResponseEntity<PromiseDetailDto> getPromiseDetail(@PathVariable("promiseId") Long promiseId,
                                                             @RequestHeader("username") String guestUsername) {
        PromiseDetailDto promiseDetail = promiseService.getPromiseDetail(promiseId, guestUsername);
        return ResponseEntity.ok(promiseDetail);
    }

    @DeleteMapping("/{promiseId}")
    @Operation(summary = "약속 삭제", description = "사용자가 만든 약속을 삭제합니다.")
    public ResponseEntity<String> deletePromise(@PathVariable("promiseId") Long promiseId,
                                                @RequestHeader("username") String hostUsername) {
        promiseService.deletePromise(promiseId, hostUsername);
        return ResponseEntity.ok("Promise deleted successfully");
    }

    @PostMapping("/update-late-status")
    @Operation(summary = "지각 상태 업데이트", description = "약속의 지각 여부를 업데이트합니다.")
    public ResponseEntity<Void> updateLateStatus(@RequestHeader("username") String username,
                                                 @RequestBody UpdateLateStatusDto updateLateStatusDto) {
        promiseService.updateLateStatus(updateLateStatusDto, username);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{promiseId}/result")
    @Operation(summary = "벌금 정산 결과 조회", description = "약속의 벌금 정산 결과를 조회합니다.")
    public ResponseEntity<PromiseResultDto> getPromiseResultDetails(@PathVariable("promiseId") Long promiseId,
                                                                    @RequestHeader("username") String guestUsername) {
        PromiseResultDto settlementDetails = promiseService.getPromiseResultDetails(promiseId);
        return ResponseEntity.ok(settlementDetails);
    }

    @PostMapping("/reward")
    @Operation(summary = "보상 지급 결정", description = "벌금 및 보상을 정산합니다.")
    public ResponseEntity<Void> decideRewards(@RequestBody RewardDto rewardDto) {
        promiseService.decideRewards(rewardDto);
        return ResponseEntity.ok().build();
    }
}