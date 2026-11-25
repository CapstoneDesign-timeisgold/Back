package jiki.jiki.friend;

import jiki.jiki.exception.ConflictException;
import jiki.jiki.friend.dto.FriendDto;
import jiki.jiki.friend.dto.FriendRequestDto;
import jiki.jiki.friend.dto.FriendRequestListDto;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private FriendRepository friendRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FriendService friendService;

    @Test
    void acceptsOnlyPendingRequestForRecipient() {
        Friend friend = pendingRequestFor("recipient");
        when(friendRepository.findById(1L)).thenReturn(Optional.of(friend));

        friendService.acceptFriendRequest("recipient", 1L);

        assertEquals(FriendStatus.ACCEPTED, friend.getStatus());
        verify(friendRepository).save(friend);
    }

    @Test
    void rejectsAttemptToProcessAnotherUsersRequest() {
        Friend friend = pendingRequestFor("recipient");
        when(friendRepository.findById(1L)).thenReturn(Optional.of(friend));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> friendService.declineFriendRequest("attacker", 1L));

        assertEquals(FriendStatus.PENDING, friend.getStatus());
        verify(friendRepository, never()).save(friend);
    }

    @Test
    void rejectsFriendRequestToSelf() {
        SiteUser user = new SiteUser();
        user.setUsername("user");
        FriendRequestDto request = new FriendRequestDto();
        request.setUsername2("user");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> friendService.sendFriendRequest("user", request));

        verify(friendRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicateActiveFriendRequest() {
        SiteUser sender = new SiteUser();
        sender.setUsername("sender");
        SiteUser recipient = new SiteUser();
        recipient.setUsername("recipient");
        FriendRequestDto request = new FriendRequestDto();
        request.setUsername2("recipient");
        when(userRepository.findByUsername("sender")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(recipient));
        when(friendRepository.existsActiveRelationship(
                org.mockito.ArgumentMatchers.eq(sender),
                org.mockito.ArgumentMatchers.eq(recipient),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> friendService.sendFriendRequest("sender", request));

        verify(friendRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestListShowsSenderAndRecipientWhileFriendListShowsViewerAndFriend() {
        SiteUser sender = new SiteUser();
        sender.setUsername("sender");
        Friend friend = pendingRequestFor("recipient");
        friend.setId(7L);
        friend.setUser1(sender);
        when(friendRepository.findPendingRequestsWithUsers("recipient", FriendStatus.PENDING))
                .thenReturn(List.of(friend));

        assertEquals(Set.of(new FriendRequestListDto(7L, "sender", "recipient")),
                friendService.getFriendRequests("recipient"));

        friend.setStatus(FriendStatus.ACCEPTED);
        when(friendRepository.findAcceptedFriendsWithUsers("recipient", FriendStatus.ACCEPTED))
                .thenReturn(List.of(friend));
        when(friendRepository.findAcceptedFriendsWithUsers("sender", FriendStatus.ACCEPTED))
                .thenReturn(List.of(friend));

        assertEquals(Set.of(new FriendDto("recipient", "sender", "ACCEPTED")), friendService.getFriends("recipient"));
        assertEquals(Set.of(new FriendDto("sender", "recipient", "ACCEPTED")), friendService.getFriends("sender"));
    }

    private Friend pendingRequestFor(String recipientUsername) {
        SiteUser recipient = new SiteUser();
        recipient.setUsername(recipientUsername);

        Friend friend = new Friend();
        friend.setUser2(recipient);
        friend.setStatus(FriendStatus.PENDING);
        return friend;
    }
}
