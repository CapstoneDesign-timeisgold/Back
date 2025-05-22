package jiki.jiki.service;

import jiki.jiki.domain.Friend;
import jiki.jiki.domain.FriendStatus;
import jiki.jiki.domain.SiteUser;
import jiki.jiki.dto.FriendDto;
import jiki.jiki.dto.FriendRequestDto;
import jiki.jiki.dto.FriendRequestListDto;
import jiki.jiki.dto.FriendResponseDto;
import jiki.jiki.repository.FriendRepository;
import jiki.jiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    //친구 초대 요청
    @Transactional
    public void sendFriendRequest(FriendRequestDto friendRequestDto) {
        Optional<SiteUser> user1 = userRepository.findByUsername(friendRequestDto.getUsername());
        Optional<SiteUser> user2 = userRepository.findByUsername(friendRequestDto.getUsername2());

        if (user1.isPresent() && user2.isPresent()) {
            Friend friend = new Friend();
            friend.setUser1(user1.get());
            friend.setUser2(user2.get());
            friend.setStatus(FriendStatus.PENDING);
            friendRepository.save(friend);
        } else {
            throw new IllegalArgumentException("Invalid usernames provided");
        }
    }

    //친구 요청 알림
    @Transactional(readOnly = true)
    public Set<FriendRequestListDto> getFriendRequests(String username) {
        return friendRepository.findByUser2UsernameAndStatus(username, FriendStatus.PENDING)
                .stream()
                .map(this::mapToFriendRequestListDto)
                .collect(Collectors.toSet());
    }

    private FriendRequestListDto mapToFriendRequestListDto(Friend friend) {
        return FriendRequestListDto.builder()
                .friendId(friend.getId())
                .username(friend.getUser1().getUsername())
                .username2(friend.getUser2().getUsername())
                .build();
    }

    //친구추가 수락
    @Transactional
    public void acceptFriendRequest(Long friendId) {
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid friend request ID"));
        friend.setStatus(FriendStatus.ACCEPTED);
        friendRepository.save(friend);
    }

    //친구추가 거절
    @Transactional
    public void declineFriendRequest(Long friendId) {
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid friend request ID"));
        friend.setStatus(FriendStatus.DECLINED);
        friendRepository.save(friend);
    }

    //친구목록
    @Transactional(readOnly = true)
    public Set<FriendResponseDto> getFriendRequestsDto(String username) {
        return friendRepository.findByUser2UsernameAndStatus(username, FriendStatus.PENDING)
                .stream()
                .map(this::mapToFriendResponseDto)
                .collect(Collectors.toSet());
    }

    private FriendResponseDto mapToFriendResponseDto(Friend friend) {
        return FriendResponseDto.builder()
                .id(friend.getId())
                .username(friend.getUser1().getUsername())
                .username2(friend.getUser2().getUsername())
                .status(friend.getStatus().name())
                .build();
    }

    @Transactional(readOnly = true)
    public Set<FriendDto> getFriends(String username) {
        return friendRepository.findByUser1UsernameOrUser2UsernameAndStatus(username, username, FriendStatus.ACCEPTED)
                .stream()
                .map(friend -> mapToFriendDto(friend, username))
                .collect(Collectors.toSet());
    }

    private FriendDto mapToFriendDto(Friend friend, String requestUsername) {
        return FriendDto.builder()
                .username(requestUsername)
                .username2(friend.getUser1().getUsername().equals(requestUsername) ? friend.getUser2().getUsername() : friend.getUser1().getUsername())
                .status(friend.getStatus().name())
                .build();
    }
}