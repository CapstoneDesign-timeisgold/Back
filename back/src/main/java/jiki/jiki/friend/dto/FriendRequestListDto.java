package jiki.jiki.friend.dto;

import jiki.jiki.friend.Friend;

public record FriendRequestListDto(Long friendId, String username, String username2) {

    public static FriendRequestListDto from(Friend friend) {
        return new FriendRequestListDto(friend.getId(), friend.getUser1().getUsername(), friend.getUser2().getUsername());
    }
}
