package jiki.jiki.controller;

import jiki.jiki.domain.FriendShip;
import jiki.jiki.dto.FriendRequestDto;
import jiki.jiki.service.FriendShipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/friendships")
public class FriendShipController {

    @Autowired
    private FriendShipService friendShipService;

    @PostMapping("/add")
    public ResponseEntity<FriendShip> addFriend(@RequestBody FriendRequestDto friendRequestDto) {
        FriendShip friendShip = friendShipService.addFriend(friendRequestDto);
        return ResponseEntity.ok(friendShip);
    }
}