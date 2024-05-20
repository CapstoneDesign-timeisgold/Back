package jiki.jiki.service;

import jiki.jiki.domain.FriendShip;
import jiki.jiki.domain.FriendShipStatus;
import jiki.jiki.domain.SiteUser;
import jiki.jiki.dto.FriendRequestDto;
import jiki.jiki.repository.FriendShipRepository;
import jiki.jiki.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FriendShipService {

    @Autowired
    private FriendShipRepository friendShipRepository;

    @Autowired
    private UserRepository userRepository;

    public FriendShip addFriend(FriendRequestDto friendRequestDto) {
        // 닉네임으로 사용자 찾기
        SiteUser user1 = userRepository.findByNickname(friendRequestDto.getNickname1())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username: " + friendRequestDto.getNickname1()));
        SiteUser user2 = userRepository.findByNickname(friendRequestDto.getNickname2())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username: " + friendRequestDto.getNickname2()));

        FriendShip friendShip = new FriendShip();
        friendShip.setUser1(user1);
        friendShip.setUser2(user2);
        friendShip.setStatus(FriendShipStatus.FRIEND);

        return friendShipRepository.save(friendShip);
    }
}