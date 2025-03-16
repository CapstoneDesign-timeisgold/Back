package jiki.jiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jiki.jiki.dto.FriendDto;
import jiki.jiki.dto.FriendRequestDto;
import jiki.jiki.dto.FriendRequestListDto;
import jiki.jiki.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/friend")
@Tag(name = "Friend API", description = "친구 관련 API")
public class FriendController {

    private final FriendService friendService;

    @PostMapping
    @Operation(summary = "친구 요청 보내기", description = "다른 사용자에게 친구 요청을 보냅니다.")
    public ResponseEntity<String> sendFriendRequest(@RequestBody FriendRequestDto friendRequestDto) {
        friendService.sendFriendRequest(friendRequestDto);
        return ResponseEntity.ok("Friend request sent");
    }

    @GetMapping("/requests/{username}")
    @Operation(summary = "친구 요청 목록 조회", description = "사용자가 받은 친구 요청 목록을 조회합니다.")
    public ResponseEntity<Set<FriendRequestListDto>> getFriendRequests(
            @PathVariable("username") String username) {
        Set<FriendRequestListDto> friendRequests = friendService.getFriendRequests(username);
        return ResponseEntity.ok(friendRequests);
    }

    @PostMapping("/accept/{friendId}")
    @Operation(summary = "친구 요청 수락", description = "받은 친구 요청을 수락합니다.")
    public ResponseEntity<String> acceptFriendRequest(
            @PathVariable("friendId") Long friendId) {
        friendService.acceptFriendRequest(friendId);
        return ResponseEntity.ok("Friend request accepted");
    }

    @PostMapping("/decline/{friendId}")
    @Operation(summary = "친구 요청 거절", description = "받은 친구 요청을 거절합니다.")
    public ResponseEntity<String> declineFriendRequest(
            @PathVariable("friendId") Long friendId) {
        friendService.declineFriendRequest(friendId);
        return ResponseEntity.ok("Friend request declined");
    }

    @GetMapping("/list/{username}")
    @Operation(summary = "친구 목록 조회", description = "사용자의 친구 목록을 조회합니다.")
    public ResponseEntity<Set<FriendDto>> getFriends(
            @PathVariable("username") String username) {
        Set<FriendDto> friends = friendService.getFriends(username);
        return ResponseEntity.ok(friends);
    }
}