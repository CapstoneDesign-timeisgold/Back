package jiki.jiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ParticipantRequestListDto {
    private Long promiseId;
    private String hostUsername;
    private String guestUsername; // 추가
    private Long participantId; // 추가
    private String title;
}