package jiki.jiki.promise;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromiseControllerTest {

    @Mock
    private PromiseService promiseService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private PromiseController promiseController;

    @Test
    void createsPromiseAsAuthenticatedUser() {
        PromiseCreateDto request = new PromiseCreateDto();
        when(authentication.getName()).thenReturn("creator");

        promiseController.createPromise(authentication, request);

        verify(promiseService).createPromise("creator", request);
    }
}
