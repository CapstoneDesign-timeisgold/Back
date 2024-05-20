package jiki.jiki.service;

import jiki.jiki.domain.FriendShip;
import jiki.jiki.domain.FriendShipStatus;
import jiki.jiki.domain.SiteUser;
import jiki.jiki.dto.FriendDto;
import jiki.jiki.dto.FriendRequestDto;
import jiki.jiki.repository.FriendShipRepository;
import jiki.jiki.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    //친구 목록 조회
    public Set<FriendDto> getFriendsByNickname(String nickname) {
        SiteUser user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("Invalid nickname: " + nickname));

        Set<FriendDto> friendsInitiated = user.getFriendshipsInitiated().stream()
                .map(friendship -> new FriendDto(friendship.getUser2().getId(), friendship.getUser2().getNickname()))
                .collect(Collectors.toSet());

        Set<FriendDto> friendsReceived = user.getFriendshipsReceived().stream()
                .map(friendship -> new FriendDto(friendship.getUser1().getId(), friendship.getUser1().getNickname()))
                .collect(Collectors.toSet());

        return Stream.concat(friendsInitiated.stream(), friendsReceived.stream())
                .collect(Collectors.toSet());
    }
}