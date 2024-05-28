package jiki.jiki.service;

import jakarta.persistence.EntityNotFoundException;
import jiki.jiki.domain.FriendShip;
import jiki.jiki.domain.FriendShipStatus;
import jiki.jiki.domain.SiteUser;
import jiki.jiki.dto.FriendDto;
import jiki.jiki.dto.FriendRequestDto;
import jiki.jiki.repository.FriendShipRepository;
import jiki.jiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Service
public class FriendShipService {

    private final FriendShipRepository friendShipRepository;
    private final UserRepository userRepository;

    // 친구 요청 보내기
    public FriendShip sendFriendRequest(String username, FriendRequestDto friendRequestDto) {
        SiteUser user1 = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + username));
        SiteUser user2 = userRepository.findByUsername(friendRequestDto.getUsername2())
                .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + friendRequestDto.getUsername2()));

        FriendShip friendShip = new FriendShip();
        friendShip.setUser1(user1);
        friendShip.setUser2(user2);
        friendShip.setStatus(FriendShipStatus.PENDING);

        return friendShipRepository.save(friendShip);
    }

    // 친구 요청 수락
    public void acceptFriendRequest(String username, Long id) {
        FriendShip friendShip = friendShipRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invalid friend request ID: " + id));

        // 인증된 사용자가 friendShip.user2인지 확인
        if (!friendShip.getUser2().getUsername().equals(username)) {
            throw new IllegalArgumentException("User not authorized to accept this friend request");
        }

        friendShip.setStatus(FriendShipStatus.ACCEPTED);
        friendShipRepository.save(friendShip);
    }

    // 친구 요청 거절
    public void declineFriendRequest(String username, Long id) {
        FriendShip friendShip = friendShipRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invalid friend request ID: " + id));

        // 인증된 사용자가 friendShip.user2인지 확인
        if (!friendShip.getUser2().getUsername().equals(username)) {
            throw new IllegalArgumentException("User not authorized to decline this friend request");
        }

        friendShip.setStatus(FriendShipStatus.DECLINED);
        friendShipRepository.save(friendShip);
    }

    // 친구 목록 조회
    public Set<FriendDto> getFriendsByUsername(String username) {
        SiteUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + username));

        Set<FriendDto> friendsInitiated = user.getFriendshipsInitiated().stream()
                .filter(friendship -> friendship.getStatus() == FriendShipStatus.ACCEPTED)
                .map(friendship -> new FriendDto(friendship.getUser2().getId(), friendship.getUser2().getUsername()))
                .collect(Collectors.toSet());

        Set<FriendDto> friendsReceived = user.getFriendshipsReceived().stream()
                .filter(friendship -> friendship.getStatus() == FriendShipStatus.ACCEPTED)
                .map(friendship -> new FriendDto(friendship.getUser1().getId(), friendship.getUser1().getUsername()))
                .collect(Collectors.toSet());

        return Stream.concat(friendsInitiated.stream(), friendsReceived.stream())
                .collect(Collectors.toSet());
    }
}