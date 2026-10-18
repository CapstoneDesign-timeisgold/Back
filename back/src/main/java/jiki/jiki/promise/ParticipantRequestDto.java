package jiki.jiki.promise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ParticipantRequestDto {
    @NotNull
    @Positive
    private Long promiseId;
    @NotBlank
    private String guestUsername;  // 초대받는 사람
}
