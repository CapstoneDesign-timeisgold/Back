package jiki.jiki.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {

    private static final String TEST_SECRET = "DsAnSkQ0x0kklOcdMPXGmuLQCiwqRgVDQAaedL60uCE=";

    private final JwtProvider jwtProvider = new JwtProvider(
            TEST_SECRET,
            86400000L
    );

    @Test
    void generatesAndValidatesToken() {
        String token = jwtProvider.generateToken("testuser");

        assertTrue(jwtProvider.validateToken(token));
        assertEquals("testuser", jwtProvider.getUsername(token));
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtProvider.generateToken("testuser");

        assertFalse(jwtProvider.validateToken(token + "tampered"));
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtProvider shortLived = new JwtProvider(
                TEST_SECRET,
                1L
        );
        String token = shortLived.generateToken("testuser");
        Thread.sleep(10);

        assertFalse(shortLived.validateToken(token));
    }
}
