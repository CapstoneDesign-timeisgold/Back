package jiki.jiki.settlement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPenaltyDto {
    private String username;
    private int penaltyAmount;
    private int rewardAmount;
}
