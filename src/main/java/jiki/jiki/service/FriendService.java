package jiki.jiki.service;

import jakarta.persistence.EntityNotFoundException;
import jiki.jiki.domain.Friend;
import jiki.jiki.domain.FriendStatus;
import jiki.jiki.domain.SiteUser;
import jiki.jiki.dto.FriendDto;
import jiki.jiki.dto.FriendRequestDto;
import jiki.jiki.repository.FriendRepository;
import jiki.jiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Service
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    // 친구 요청 보내기
    public Friend sendFriendRequest(String username, FriendRequestDto friendRequestDto) {
        SiteUser user1 = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + username));
        SiteUser user2 = userRepository.findByUsername(friendRequestDto.getUsername2())
                .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + friendRequestDto.getUsername2()));

        Friend friend = new Friend();
        friend.setUser1(user1);
        friend.setUser2(user2);
        friend.setStatus(FriendStatus.PENDING);

        return friendRepository.save(friend);
    }

    // 친구 요청 수락
    public void acceptFriendRequest(String username, Long id) {
        Friend friend = friendRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invalid friend request ID: " + id));

        // 인증된 사용자가 Friend.user2인지 확인
        if (!friend.getUser2().getUsername().equals(username)) {
            throw new IllegalArgumentException("User not authorized to accept this friend request");
        }

        friend.setStatus(FriendStatus.ACCEPTED);
        friendRepository.save(friend);
    }

    // 친구 요청 거절
    public void declineFriendRequest(String username, Long id) {
        Friend friend = friendRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invalid friend request ID: " + id));

        // 인증된 사용자가 Friend.user2인지 확인
        if (!friend.getUser2().getUsername().equals(username)) {
            throw new IllegalArgumentException("User not authorized to decline this friend request");
        }

        friend.setStatus(FriendStatus.DECLINED);
        friendRepository.save(friend);
    }

    // 친구 목록 조회
    public Set<FriendDto> getFriendsByUsername(String username) {
        SiteUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Invalid username: " + username));

        Set<FriendDto> friendsInitiated = user.getFriendsInitiated().stream()
                .filter(Friend -> Friend.getStatus() == FriendStatus.ACCEPTED)
                .map(Friend -> new FriendDto(Friend.getUser2().getId(), Friend.getUser2().getUsername()))
                .collect(Collectors.toSet());

        Set<FriendDto> friendsReceived = user.getFriendsReceived().stream()
                .filter(Friend -> Friend.getStatus() == FriendStatus.ACCEPTED)
                .map(Friend -> new FriendDto(Friend.getUser1().getId(), Friend.getUser1().getUsername()))
                .collect(Collectors.toSet());

        return Stream.concat(friendsInitiated.stream(), friendsReceived.stream())
                .collect(Collectors.toSet());
    }
}