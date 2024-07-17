package jiki.jiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromiseDetailDto {
    private String title;
    private String date;
    private String time;
    private double latitude;
    private double longitude;
    private int penalty;
    private Set<String> participantUsernames;
    private Long promiseId;
    private Set<Long> participantIds;
}