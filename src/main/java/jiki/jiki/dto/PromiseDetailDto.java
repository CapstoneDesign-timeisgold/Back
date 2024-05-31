package jiki.jiki.dto;

import lombok.Data;

import java.util.Set;

@Data
public class PromiseDetailDto {
    private String title;
    private String date;
    private String time;
    private String location;
    private int penalty;
    private Set<String> participantUsernames;
    private Long promiseId;
}