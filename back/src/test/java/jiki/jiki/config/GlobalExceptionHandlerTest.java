package jiki.jiki.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void unexpectedErrorDoesNotExposeExceptionDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Exception failure = new RuntimeException("jdbc:mariadb://internal-db/private_schema");

        ResponseEntity<Map<String, String>> response = handler.handleGeneral(failure);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(Map.of("error", "Internal server error"), response.getBody());
    }
}
