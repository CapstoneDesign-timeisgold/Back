//친구목록 dto

package jiki.jiki.dto;

import lombok.Data;

@Data
public class FriendDto {
    private Long id;
    private String nickname;

    public FriendDto(Long id, String nickname) {
        this.id = id;
        this.nickname = nickname;
    }
}