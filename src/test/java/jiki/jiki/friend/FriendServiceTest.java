package jiki.jiki.friend;

import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

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

        assertThrows(IllegalArgumentException.class,
                () -> friendService.declineFriendRequest("attacker", 1L));

        assertEquals(FriendStatus.PENDING, friend.getStatus());
        verify(friendRepository, never()).save(friend);
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
