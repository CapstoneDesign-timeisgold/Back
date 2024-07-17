package jiki.jiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ParticipantRequestDto {
    private Long promiseId;
    private String guestUsername;  // 초대받는 사람
}