package jiki.jiki.promise;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateLateStatusDto {
    @NotNull
    @Positive
    private Long promiseId;
    @AssertTrue(message = "도착 확인 요청의 arrival은 true여야 합니다.")
    private boolean arrival;
}
