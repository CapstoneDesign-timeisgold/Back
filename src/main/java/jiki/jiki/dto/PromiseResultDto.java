package jiki.jiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class PromiseResultDto {
    private Long promiseId;
    private List<UserPenaltyDto> lateUsers;
    private List<UserPenaltyDto> onTimeUsers;
    private int totalPenalty;
}
