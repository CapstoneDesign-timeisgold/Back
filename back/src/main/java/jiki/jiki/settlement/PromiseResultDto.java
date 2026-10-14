package jiki.jiki.settlement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromiseResultDto {
    private Long promiseId;
    private List<UserPenaltyDto> lateUsers;
    private List<UserPenaltyDto> onTimeUsers;
    private int totalPenalty;
    // Signed adjustment outside participant rewards/penalties: credit (+), rounding subsidy (-).
    private int adminAmount;
}
