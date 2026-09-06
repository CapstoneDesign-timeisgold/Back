package jiki.jiki.promise;

import jiki.jiki.config.ConflictException;
import jiki.jiki.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ParticipationDeadlineHttpTest {
    private final PromiseService service = mock(PromiseService.class);
    private final UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("guest", null);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PromiseController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void missingCreationDeadlineReturns400() throws Exception {
        mvc.perform(post("/promise").principal(principal).contentType(MediaType.APPLICATION_JSON).content("""
                {"date":"2026-09-06","time":"12:30","title":"점심","penalty":1000,"latitude":37,"longitude":127}
                """)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidDeadlineFormatReturns400() throws Exception {
        mvc.perform(patch("/promise/1/participation-deadline").principal(principal)
                .contentType(MediaType.APPLICATION_JSON).content("{\"participationDeadline\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void deadlinePatchParsesIsoDateTime() throws Exception {
        mvc.perform(patch("/promise/1/participation-deadline").principal(principal)
                .contentType(MediaType.APPLICATION_JSON).content("{\"participationDeadline\":\"2026-09-06T12:25:00\"}"))
                .andExpect(status().isNoContent());
        verify(service).updateParticipationDeadline(eq("guest"), eq(1L),
                argThat(dto -> dto.getParticipationDeadline().equals(LocalDateTime.of(2026, 9, 6, 12, 25))));
    }

    @Test
    void cancellationUsesAuthenticatedUserAndReportsClosedParticipationAs409() throws Exception {
        doThrow(new ConflictException("Participation has closed")).when(service).cancelParticipation("guest", 2L);
        mvc.perform(post("/promise/cancel/2").principal(principal))
                .andExpect(status().isConflict()).andExpect(jsonPath("error").value("Participation has closed"));
    }
}
