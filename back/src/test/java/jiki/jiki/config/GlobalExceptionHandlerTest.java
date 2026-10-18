package jiki.jiki.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @Test void unexpectedIllegalStateIs500AndDoesNotExposeInternalDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new BrokenController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/broken")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("error").value("Internal server error"));
    }

    @Test void unexpectedRuntimeErrorDoesNotExposeExceptionDetails() {
        var response = new GlobalExceptionHandler().handleGeneral(
                new RuntimeException("jdbc:mariadb://internal-db/private_schema"));
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Map.of("error", "Internal server error"), response.getBody());
    }

    @RestController
    static class BrokenController {
        @GetMapping("/broken")
        String broken() {
            throw new IllegalStateException("private SQL connection details");
        }
    }
}
