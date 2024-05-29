package jiki.jiki.dto;

import lombok.Data;

import java.util.Set;

@Data
public class ParticipantDto {
    private Long promiseId;
    private Set<String> friendUsernames;  // 친구 닉네임들
}