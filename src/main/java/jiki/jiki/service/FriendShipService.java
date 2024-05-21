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

    public FriendShip sendFriendRequest(FriendRequestDto friendRequestDto) {
        SiteUser user1 = userRepository.findByNickname(friendRequestDto.getNickname1())
                .orElseThrow(() -> new IllegalArgumentException("Invalid nickname: " + friendRequestDto.getNickname1()));
        SiteUser user2 = userRepository.findByNickname(friendRequestDto.getNickname2())
                .orElseThrow(() -> new IllegalArgumentException("Invalid nickname: " + friendRequestDto.getNickname2()));

        FriendShip friendShip = new FriendShip();
        friendShip.setUser1(user1);
        friendShip.setUser2(user2);
        friendShip.setStatus(FriendShipStatus.PENDING);

        return friendShipRepository.save(friendShip);
    }

    public void acceptFriendRequest(Long id) {
        FriendShip friendShip = friendShipRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid friend request ID: " + id));
        friendShip.setStatus(FriendShipStatus.ACCEPTED);
        friendShipRepository.save(friendShip);
    }

    public void declineFriendRequest(Long id) {
        FriendShip friendShip = friendShipRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid friend request ID: " + id));
        friendShip.setStatus(FriendShipStatus.DECLINED);
        friendShipRepository.save(friendShip);
    }

    public Set<FriendDto> getFriendsByNickname(String nickname) {
        SiteUser user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("Invalid nickname: " + nickname));

        Set<FriendDto> friendsInitiated = user.getFriendshipsInitiated().stream()
                .filter(friendship -> friendship.getStatus() == FriendShipStatus.ACCEPTED)
                .map(friendship -> new FriendDto(friendship.getUser2().getId(), friendship.getUser2().getNickname()))
                .collect(Collectors.toSet());

        Set<FriendDto> friendsReceived = user.getFriendshipsReceived().stream()
                .filter(friendship -> friendship.getStatus() == FriendShipStatus.ACCEPTED)
                .map(friendship -> new FriendDto(friendship.getUser1().getId(), friendship.getUser1().getNickname()))
                .collect(Collectors.toSet());

        return Stream.concat(friendsInitiated.stream(), friendsReceived.stream())
                .collect(Collectors.toSet());
    }
}