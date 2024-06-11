package jiki.jiki.dto;

import lombok.Data;

@Data
public class FriendResponseDto {
    private Long id;
    private String username; // 친구 요청 보낸 사람 username
    private String username2; // 친구 요청 받은 사람 username
    private String status; // 요청 상태 (PENDING, ACCEPTED, DECLINED)
}
