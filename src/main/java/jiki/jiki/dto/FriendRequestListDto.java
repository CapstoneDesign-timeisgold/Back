package jiki.jiki.dto;

import lombok.Data;

@Data
public class FriendRequestListDto {
    private Long friendId; // 친구 요청 ID
    private String username; // 요청 보내는 사람 username
    private String username2; // 요청 받는 사람 username
}
