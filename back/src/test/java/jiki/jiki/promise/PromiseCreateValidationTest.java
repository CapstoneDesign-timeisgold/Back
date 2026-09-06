package jiki.jiki.promise;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PromiseCreateValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private PromiseRepository promiseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @InjectMocks
    private PromiseService promiseService;

    @Test
    void rejectsInvalidPromiseCreateRequestFields() {
        PromiseCreateDto request = PromiseCreateDto.builder()
                .date("2026/09/03")
                .time("25:61")
                .penalty(-1)
                .title(" ")
                .latitude(91)
                .longitude(181)
                .build();

        Set<String> invalidFields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertEquals(Set.of("date", "time", "participationDeadline", "penalty", "title", "latitude", "longitude"), invalidFields);
    }

    @Test
    void rejectsNonexistentDateBeforeDatabaseAccess() {
        PromiseCreateDto request = PromiseCreateDto.builder()
                .date("2026-02-30")
                .time("10:30")
                .penalty(1_000)
                .title("점심 약속")
                .latitude(37.5665)
                .longitude(126.9780)
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> promiseService.createPromise("creator", request));

        assertTrue(exception.getMessage().contains("valid ISO values"));
        verifyNoInteractions(userRepository, promiseRepository, participantRepository);
    }
}
