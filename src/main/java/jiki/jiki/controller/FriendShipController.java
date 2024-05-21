package jiki.jiki.controller;

import jiki.jiki.domain.FriendShip;
import jiki.jiki.dto.FriendDto;
import jiki.jiki.dto.FriendRequestDto;
import jiki.jiki.service.FriendShipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/friendships")
public class FriendShipController {

    @Autowired
    private FriendShipService friendShipService;

    @PostMapping("/add")
    public ResponseEntity<FriendShip> sendFriendRequest(@RequestBody FriendRequestDto friendRequestDto) {
        FriendShip friendShip = friendShipService.sendFriendRequest(friendRequestDto);
        return ResponseEntity.ok(friendShip);
    }

    @PostMapping("/accept/{id}")
    public ResponseEntity<String> acceptFriendRequest(@PathVariable Long id) {
        friendShipService.acceptFriendRequest(id);
        return ResponseEntity.ok("Friend request accepted");
    }

    @PostMapping("/decline/{id}")
    public ResponseEntity<String> declineFriendRequest(@PathVariable Long id) {
        friendShipService.declineFriendRequest(id);
        return ResponseEntity.ok("Friend request declined");
    }

    @GetMapping("/friends/{nickname}")
    public ResponseEntity<Set<FriendDto>> getFriendsByNickname(@PathVariable("nickname") String nickname) {
        Set<FriendDto> friends = friendShipService.getFriendsByNickname(nickname);
        return ResponseEntity.ok(friends);
    }
}