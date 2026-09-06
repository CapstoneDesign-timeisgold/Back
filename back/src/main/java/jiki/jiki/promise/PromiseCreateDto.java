package jiki.jiki.promise;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromiseCreateDto {
    @NotBlank(message = "약속 날짜는 필수입니다.")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "약속 날짜는 yyyy-MM-dd 형식이어야 합니다.")
    private String date;

    @NotBlank(message = "약속 시간은 필수입니다.")
    @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d", message = "약속 시간은 HH:mm 형식이어야 합니다.")
    private String time;

    @NotNull(message = "참여 마감 시각은 필수입니다.")
    private LocalDateTime participationDeadline;

    @PositiveOrZero(message = "벌금은 0 이상이어야 합니다.")
    private int penalty;

    @NotBlank(message = "약속 제목은 필수입니다.")
    @Size(max = 100, message = "약속 제목은 100자 이하여야 합니다.")
    private String title;

    @DecimalMin(value = "-90.0", message = "위도는 -90에서 90 사이여야 합니다.")
    @DecimalMax(value = "90.0", message = "위도는 -90에서 90 사이여야 합니다.")
    private double latitude;

    @DecimalMin(value = "-180.0", message = "경도는 -180에서 180 사이여야 합니다.")
    @DecimalMax(value = "180.0", message = "경도는 -180에서 180 사이여야 합니다.")
    private double longitude;
}
