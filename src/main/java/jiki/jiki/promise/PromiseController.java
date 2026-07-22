package jiki.jiki.promise;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(promise);
    }

    @PostMapping("/invitation")
    @Operation(summary = "친구 초대", description = "약속에 친구를 초대합니다.")
    public ResponseEntity<String> inviteFriends(Authentication authentication,
                                                @RequestBody ParticipantRequestDto participantRequestDto) {
        promiseService.inviteParticipant(authentication.getName(), participantRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Friends invited to the promise");
    }

    @GetMapping("/invitation")
    @Operation(summary = "초대 요청 조회", description = "사용자가 받은 약속 초대 목록을 조회합니다.")
    public ResponseEntity<Set<ParticipantRequestListDto>> getPromiseInvitations(Authentication authentication) {
        Set<ParticipantRequestListDto> invitations = promiseService.getPromiseInvitations(authentication.getName());
        return ResponseEntity.ok(invitations);
    }

    @PostMapping("/accept/{participantId}")
    @Operation(summary = "초대 수락", description = "약속 초대를 수락합니다.")
    public ResponseEntity<PromiseDetailDto> acceptPromiseInvitation(Authentication authentication,
                                                                    @PathVariable("participantId") Long participantId) {
        PromiseDetailDto promiseDetail = promiseService.acceptPromiseInvitation(authentication.getName(), participantId);
        return ResponseEntity.ok(promiseDetail);
    }

    @PostMapping("/decline/{participantId}")
    @Operation(summary = "초대 거절", description = "약속 초대를 거절합니다.")
    public ResponseEntity<String> declinePromiseInvitation(Authentication authentication,
                                                           @PathVariable("participantId") Long participantId) {
        promiseService.declinePromiseInvitation(authentication.getName(), participantId);
        return ResponseEntity.ok("Promise invitation declined");
    }

    @GetMapping
    @Operation(summary = "약속 리스트 조회", description = "사용자의 약속 리스트를 조회합니다.")
    public ResponseEntity<List<PromiseListDto>> getPromiseList(Authentication authentication) {
        List<PromiseListDto> promises = promiseService.getPromiseList(authentication.getName());
        return ResponseEntity.ok(promises);
    }

    @GetMapping("/{promiseId}")
    @Operation(summary = "약속 상세 조회", description = "약속의 상세 정보를 조회합니다.")
    public ResponseEntity<PromiseDetailDto> getPromiseDetail(@PathVariable("promiseId") Long promiseId,
                                                             Authentication authentication) {
        PromiseDetailDto promiseDetail = promiseService.getPromiseDetail(promiseId, authentication.getName());
        return ResponseEntity.ok(promiseDetail);
    }

    @DeleteMapping("/{promiseId}")
    @Operation(summary = "약속 삭제", description = "사용자가 만든 약속을 삭제합니다.")
    public ResponseEntity<Void> deletePromise(@PathVariable("promiseId") Long promiseId,
                                              Authentication authentication) {
        promiseService.deletePromise(promiseId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/update-late-status")
    @Operation(summary = "지각 상태 업데이트", description = "약속의 지각 여부를 업데이트합니다.")
    public ResponseEntity<Void> updateLateStatus(Authentication authentication,
                                                 @RequestBody UpdateLateStatusDto updateLateStatusDto) {
        promiseService.updateLateStatus(updateLateStatusDto, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}