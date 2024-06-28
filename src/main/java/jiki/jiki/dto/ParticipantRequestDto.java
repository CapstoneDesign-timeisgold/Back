package jiki.jiki.dto;

import lombok.Data;

@Data
public class ParticipantRequestDto {
    private Long promiseId;
    private String guestUsername;  // 초대받는 사람
}