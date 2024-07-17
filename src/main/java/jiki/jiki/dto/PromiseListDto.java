package jiki.jiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromiseListDto {
    private String title;
    private String date;
    private String time;
    private Long promiseId;
    private String creatorUsername;
}