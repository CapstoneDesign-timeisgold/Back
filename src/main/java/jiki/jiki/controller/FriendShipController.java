package jiki.jiki.controller;

import jiki.jiki.domain.FriendShip;
import jiki.jiki.dto.FriendDto;
import jiki.jiki.dto.FriendRequestDto;
import jiki.jiki.service.FriendShipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequiredArgsConstructor
public class FriendShipController {

    private final FriendShipService friendShipService;

    @PostMapping("/friend/add")
    public ResponseEntity<FriendShip> sendFriendRequest(@RequestHeader("username") String username, @RequestBody FriendRequestDto friendRequestDto) {
        FriendShip friendShip = friendShipService.sendFriendRequest(username, friendRequestDto);
        return ResponseEntity.ok(friendShip);
    }

    @PostMapping("friend/accept/{id}")
    public ResponseEntity<String> acceptFriendRequest(@RequestHeader("username") String username, @PathVariable Long id) {
        friendShipService.acceptFriendRequest(username, id);
        return ResponseEntity.ok("Friend request accepted");
    }

    @PostMapping("friend/decline/{id}")
    public ResponseEntity<String> declineFriendRequest(@RequestHeader("username") String username, @PathVariable Long id) {
        friendShipService.declineFriendRequest(username, id);
        return ResponseEntity.ok("Friend request declined");
    }

    @GetMapping("/friend/{username}")
    public ResponseEntity<Set<FriendDto>> getFriendsByUsername(@PathVariable("username") String username) {
        Set<FriendDto> friends = friendShipService.getFriendsByUsername(username);
        return ResponseEntity.ok(friends);
    }
}
