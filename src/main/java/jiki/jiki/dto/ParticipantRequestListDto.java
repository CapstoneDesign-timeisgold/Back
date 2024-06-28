package jiki.jiki.dto;

import lombok.Data;

@Data
public class ParticipantRequestListDto {
    private Long promiseId;
    private String hostUsername;
    private String guestUsername; // 추가
    private Long participantId; // 추가
}