package jiki.jiki.friend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendControllerTest {

    @Mock
    private FriendService friendService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private FriendController friendController;

    @Test
    void sendsFriendRequestAsAuthenticatedUser() {
        FriendRequestDto request = new FriendRequestDto();
        request.setUsername2("receiver");
        when(authentication.getName()).thenReturn("sender");

        friendController.sendFriendRequest(authentication, request);

        verify(friendService).sendFriendRequest("sender", request);
    }

    @Test
    void acceptsFriendRequestAsAuthenticatedRecipient() {
        when(authentication.getName()).thenReturn("recipient");

        friendController.acceptFriendRequest(authentication, 1L);

        verify(friendService).acceptFriendRequest("recipient", 1L);
    }

    @Test
    void declinesFriendRequestAsAuthenticatedRecipient() {
        when(authentication.getName()).thenReturn("recipient");

        friendController.declineFriendRequest(authentication, 1L);

        verify(friendService).declineFriendRequest("recipient", 1L);
    }
}
