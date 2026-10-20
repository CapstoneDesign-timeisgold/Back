package jiki.jiki.promise;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ParticipantDetailDto {
    private Long participantId;
    private String username;
    private ParticipantStatus status;
    private boolean arrival;
    private LocalDateTime arrivedAt;
    private ArrivalStatus arrivalStatus;

    public static ParticipantDetailDto from(Participant participant) {
        return new ParticipantDetailDto(participant.getId(), participant.getGuest().getUsername(),
                participant.getStatus(), participant.isArrival(), participant.getArrivedAt(), participant.getArrivalStatus());
    }
}
