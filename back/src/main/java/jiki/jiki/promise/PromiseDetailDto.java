package jiki.jiki.promise;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.List;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromiseDetailDto {
    private String title;
    private String date;
    private String time;
    private LocalDateTime participationDeadline;
    private boolean participationOpen;
    private boolean participationDeadlineEditable;
    private double latitude;
    private double longitude;
    private int penalty;
    private Set<String> participantUsernames;
    private Long promiseId;
    private Set<Long> participantIds;
    private List<ParticipantDetailDto> participants;
}
