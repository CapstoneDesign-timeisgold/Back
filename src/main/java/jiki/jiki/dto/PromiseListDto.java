package jiki.jiki.dto;

import lombok.Data;

@Data
public class PromiseListDto {
    private String title;
    private String date;
    private String time;
    private Long promiseId; // promiseId 필드 추가
}