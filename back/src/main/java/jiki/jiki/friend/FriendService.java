package jiki.jiki.friend;

import jakarta.persistence.EntityNotFoundException;
import jiki.jiki.exception.ConflictException;
import jiki.jiki.friend.dto.FriendDto;
import jiki.jiki.friend.dto.FriendRequestDto;
import jiki.jiki.friend.dto.FriendRequestListDto;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    //친구 초대 요청
    @Transactional
    public void sendFriendRequest(String senderUsername, FriendRequestDto friendRequestDto) {
        SiteUser sender = findUser(senderUsername);
        SiteUser recipient = findUser(friendRequestDto.getUsername2());

        if (senderUsername.equals(friendRequestDto.getUsername2())) {
            throw new IllegalArgumentException("Cannot send a friend request to yourself");
        }

        if (friendRepository.existsActiveRelationship(
                sender,
                recipient,
                List.of(FriendStatus.PENDING, FriendStatus.ACCEPTED))) {
            throw new ConflictException("An active friend request or friendship already exists");
        }

        Friend friend = new Friend();
        friend.setUser1(sender);
        friend.setUser2(recipient);
        friend.setStatus(FriendStatus.PENDING);
        friendRepository.save(friend);
    }

    private SiteUser findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    //친구 요청 알림
    @Transactional(readOnly = true)
    public Set<FriendRequestListDto> getFriendRequests(String username) {
        return friendRepository.findPendingRequestsWithUsers(username, FriendStatus.PENDING)
                .stream()
                .map(FriendRequestListDto::from)
                .collect(Collectors.toSet());
    }

    //친구추가 수락
    @Transactional
    public void acceptFriendRequest(String recipientUsername, Long friendId) {
        respondToRequest(recipientUsername, friendId, FriendStatus.ACCEPTED);
    }

    //친구추가 거절
    @Transactional
    public void declineFriendRequest(String recipientUsername, Long friendId) {
        respondToRequest(recipientUsername, friendId, FriendStatus.DECLINED);
    }

    private void respondToRequest(String recipientUsername, Long friendId, FriendStatus status) {
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid friend request ID"));
        if (!friend.getUser2().getUsername().equals(recipientUsername)) {
            throw new AccessDeniedException("User not authorized to process this friend request");
        }

        if (friend.getStatus() != FriendStatus.PENDING) {
            throw new ConflictException("Friend request is no longer pending");
        }
        friend.setStatus(status);
        friendRepository.save(friend);
    }

    @Transactional(readOnly = true)
    public Set<FriendDto> getFriends(String username) {
        return friendRepository.findAcceptedFriendsWithUsers(username, FriendStatus.ACCEPTED)
                .stream()
                .map(friend -> FriendDto.from(friend, username))
                .collect(Collectors.toSet());
    }
}
