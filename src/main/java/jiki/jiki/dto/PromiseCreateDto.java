package jiki.jiki.dto;

import lombok.Data;

@Data
public class PromiseCreateDto {
    private String date;
    private String time;
    private String location;
    private int penalty;
    private String title;
    private String creatorNickname;
    private String creatorUsername;
}