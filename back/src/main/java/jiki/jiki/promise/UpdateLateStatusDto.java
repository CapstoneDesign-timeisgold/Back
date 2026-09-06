package jiki.jiki.promise;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UpdateLateStatusDto {
    private Long promiseId;
    private boolean arrival;
}