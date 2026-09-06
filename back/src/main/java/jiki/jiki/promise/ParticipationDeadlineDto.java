package jiki.jiki.promise;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParticipationDeadlineDto {
    @NotNull(message = "참여 마감 시각은 필수입니다.")
    private LocalDateTime participationDeadline;
}
