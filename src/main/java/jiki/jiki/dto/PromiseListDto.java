package jiki.jiki.dto;

import lombok.Data;

@Data
public class PromiseListDto {
    private String title;
    private String date;
    private String time;
    private Long promiseId;
    private String creatorUsername;
}