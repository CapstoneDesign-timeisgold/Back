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
    public ResponseEntity<FriendShip> sendFriendRequest(@RequestBody FriendRequestDto friendRequestDto) {
        FriendShip friendShip = friendShipService.sendFriendRequest(friendRequestDto);
        return ResponseEntity.ok(friendShip);
    }

    @PostMapping("/friend/accept/{id}")
    public ResponseEntity<String> acceptFriendRequest(@PathVariable Long id) {
        friendShipService.acceptFriendRequest(id);
        return ResponseEntity.ok("Friend request accepted");
    }

    @PostMapping("/friend/decline/{id}")
    public ResponseEntity<String> declineFriendRequest(@PathVariable Long id) {
        friendShipService.declineFriendRequest(id);
        return ResponseEntity.ok("Friend request declined");
    }

    @GetMapping("/friend/{nickname}")
    public ResponseEntity<Set<FriendDto>> getFriendsByNickname(@PathVariable("nickname") String nickname) {
        Set<FriendDto> friends = friendShipService.getFriendsByNickname(nickname);
        return ResponseEntity.ok(friends);
    }
}