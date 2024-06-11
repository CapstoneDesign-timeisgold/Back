package jiki.jiki.controller;

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
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/friend")
    public ResponseEntity<String> sendFriendRequest(@RequestBody FriendRequestDto friendRequestDto) {
        friendService.sendFriendRequest(friendRequestDto);
        return ResponseEntity.ok("Friend request sent");
    }

    @GetMapping("/friend/requests/{username}")
    public ResponseEntity<Set<FriendRequestListDto>> getFriendRequests(@PathVariable("username") String username) {
        Set<FriendRequestListDto> friendRequests = friendService.getFriendRequests(username);
        return ResponseEntity.ok(friendRequests);
    }

    @PostMapping("/friend/accept/{friendId}")
    public ResponseEntity<String> acceptFriendRequest(@PathVariable("friendId") Long friendId) {
        friendService.acceptFriendRequest(friendId);
        return ResponseEntity.ok("Friend request accepted");
    }

    @PostMapping("/friend/decline/{friendId}")
    public ResponseEntity<String> declineFriendRequest(@PathVariable("friendId") Long friendId) {
        friendService.declineFriendRequest(friendId);
        return ResponseEntity.ok("Friend request declined");
    }

    @GetMapping("/friend/list/{username}")
    public ResponseEntity<Set<FriendDto>> getFriends(@PathVariable("username") String username) {
        Set<FriendDto> friends = friendService.getFriends(username);
        return ResponseEntity.ok(friends);
    }
}