package jiki.jiki.user;

import jiki.jiki.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemAccountAuthenticationTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock JwtProvider jwt;
    @InjectMocks UserSecurityService security;

    @Test void systemAccountCannotLoginEvenWithItsOldPassword() {
        systemAccount();
        assertThrows(IllegalStateException.class,
                () -> security.authenticateUser(Map.of("username", "treasury", "password", "old-password")));
        verifyNoInteractions(passwords, jwt);
    }

    @Test void systemAccountCannotAuthenticateWithAnExistingJwt() {
        systemAccount();
        assertThrows(UsernameNotFoundException.class, () -> security.loadUserByUsername("treasury"));
    }

    @Test void normalMemberLoginStillIssuesToken() {
        SiteUser member = new SiteUser();
        member.setUsername("member"); member.setNickname("Member"); member.setPassword("encoded");
        when(users.findByUsername("member")).thenReturn(Optional.of(member));
        when(passwords.matches("password", "encoded")).thenReturn(true);
        when(jwt.generateToken("member")).thenReturn("token");
        assertEquals("token", security.authenticateUser(Map.of("username", "member", "password", "password")).getToken());
        assertEquals("member", security.loadUserByUsername("member").getUsername());
    }

    private void systemAccount() {
        SiteUser account = new SiteUser();
        account.setUsername("treasury"); account.setSystemAccountType(SystemAccountType.SETTLEMENT);
        when(users.findByUsername("treasury")).thenReturn(Optional.of(account));
    }
}
