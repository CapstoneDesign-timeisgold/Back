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

    @Transactional(readOnly = true)
    public Set<FriendRequestListDto> getFriendRequests(String username) {
        return friendRepository.findByUser2UsernameAndStatus(username, FriendStatus.PENDING)
                .stream()
                .map(this::mapToFriendRequestListDto)
                .collect(Collectors.toSet());
    }

    private FriendRequestListDto mapToFriendRequestListDto(Friend friend) {
        FriendRequestListDto friendRequestListDto = new FriendRequestListDto();
        friendRequestListDto.setFriendId(friend.getId()); // friendId 설정
        friendRequestListDto.setUsername(friend.getUser1().getUsername()); // 친구 요청 보낸 사용자의 username 설정
        friendRequestListDto.setUsername2(friend.getUser2().getUsername()); // 친구 요청 받은 사용자의 username 설정
        return friendRequestListDto;
    }

    @Transactional
    public void acceptFriendRequest(Long friendId) {
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid friend request ID"));
        friend.setStatus(FriendStatus.ACCEPTED);
        friendRepository.save(friend);
    }

    @Transactional
    public void declineFriendRequest(Long friendId) {
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid friend request ID"));
        friend.setStatus(FriendStatus.DECLINED);
        friendRepository.save(friend);
    }

    @Transactional(readOnly = true)
    public Set<FriendResponseDto> getFriendRequestsDto(String username) {
        return friendRepository.findByUser2UsernameAndStatus(username, FriendStatus.PENDING)
                .stream()
                .map(this::mapToFriendResponseDto)
                .collect(Collectors.toSet());
    }

    private FriendResponseDto mapToFriendResponseDto(Friend friend) {
        FriendResponseDto friendResponseDto = new FriendResponseDto();
        friendResponseDto.setId(friend.getId());
        friendResponseDto.setUsername(friend.getUser1().getUsername());
        friendResponseDto.setUsername2(friend.getUser2().getUsername());
        friendResponseDto.setStatus(friend.getStatus().name());
        return friendResponseDto;
    }

    @Transactional(readOnly = true)
    public Set<FriendDto> getFriends(String username) {
        return friendRepository.findByUser1UsernameOrUser2UsernameAndStatus(username, username, FriendStatus.ACCEPTED)
                .stream()
                .map(this::mapToFriendDto)
                .collect(Collectors.toSet());
    }

    private FriendDto mapToFriendDto(Friend friend) {
        FriendDto friendDto = new FriendDto();
        friendDto.setUsername(friend.getUser1().getUsername());
        friendDto.setUsername2(friend.getUser2().getUsername());
        friendDto.setStatus(friend.getStatus().name());
        return friendDto;
    }
}