package jiki.jiki.promise;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromiseListDto {
    private String title;
    private String date;
    private String time;
    private LocalDateTime participationDeadline;
    private boolean participationOpen;
    private Long promiseId;
    private String creatorUsername;
}
