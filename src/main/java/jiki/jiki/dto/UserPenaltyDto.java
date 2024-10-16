package jiki.jiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UserPenaltyDto {
    private String username;
    private int penaltyAmount;
    private int rewardAmount;
}