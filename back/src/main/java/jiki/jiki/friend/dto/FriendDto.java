package jiki.jiki.friend.dto;

import jiki.jiki.friend.Friend;

public record FriendDto(String username, String username2, String status) {

    public static FriendDto from(Friend friend, String username) {
        String sender = friend.getUser1().getUsername();
        String recipient = friend.getUser2().getUsername();
        String otherUsername = sender.equals(username) ? recipient : sender;
        return new FriendDto(username, otherUsername, friend.getStatus().name());
    }
}
