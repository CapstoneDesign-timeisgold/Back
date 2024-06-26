package jiki.jiki.dto;

import lombok.Data;

@Data
public class PromiseCreateDto {
    private String date;
    private String time;
    private int penalty;
    private String title;
    private String creatorUsername;
    private double latitude;
    private double longitude;
}