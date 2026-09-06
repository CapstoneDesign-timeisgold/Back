package jiki.jiki.promise;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ParticipantDetailDto {
    private Long participantId;
    private String username;
    private ParticipantStatus status;
    private boolean arrival;
}
