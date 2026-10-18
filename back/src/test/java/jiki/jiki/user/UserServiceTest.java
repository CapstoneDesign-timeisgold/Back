package jiki.jiki.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import jiki.jiki.config.ConflictException;
import java.sql.SQLException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @InjectMocks UserService service;

    @ParameterizedTest
    @ValueSource(strings = {"admin", "ADMIN", "AdMiN", " admin ", "ａｄｍｉｎ"})
    void rejectsReservedNamesBeforeAnyDatabaseWrite(String username) {
        assertThrows(IllegalArgumentException.class, () -> service.createUser(signup(username)));
        verifyNoInteractions(users, passwords);
    }

    @Test void normalSignupCannotCreateASystemAccount() {
        when(users.findByUsername("member")).thenReturn(Optional.empty());
        when(passwords.encode("password")).thenReturn("encoded");
        service.createUser(signup("member"));
        ArgumentCaptor<SiteUser> saved = ArgumentCaptor.forClass(SiteUser.class);
        verify(users).saveAndFlush(saved.capture());
        assertFalse(saved.getValue().isSystemAccount());
        assertNull(saved.getValue().getSystemAccountType());
        assertEquals("encoded", saved.getValue().getPassword());
    }

    private UserSignupDto signup(String username) {
        return new UserSignupDto(username, "password", "nickname", "member@example.test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mariadb", "h2"})
    void concurrentUniqueViolationReturnsConflict(String database) {
        SQLException sql = database.equals("mariadb")
                ? new SQLException("duplicate key", "23000", 1062)
                : new SQLException("duplicate key", "23505", 23505);
        when(passwords.encode("password")).thenReturn("encoded");
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL details", sql));
        assertThrows(ConflictException.class, () -> service.createUser(signup("member")));
    }

    @Test void unrelatedIntegrityFailureIsNotMisreportedAsDuplicateSignup() {
        when(passwords.encode("password")).thenReturn("encoded");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("NOT NULL failure",
                new SQLException("not null", "23502"));
        when(users.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.createUser(signup("member"))));
    }
}
