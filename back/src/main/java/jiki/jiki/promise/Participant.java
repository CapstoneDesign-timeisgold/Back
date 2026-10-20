package jiki.jiki.promise;

import jakarta.persistence.*;
import jiki.jiki.user.SiteUser;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;

@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_participant_promise_guest",
        columnNames = {"promise_id", "guest_id"}
))
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promise_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Promise promise;  // 약속 정보

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private SiteUser host;  // 초대한 사람

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private SiteUser guest;  // 초대받은 사람

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ParticipantStatus status;  // 참여 상태

    private boolean arrival;  // 도착 여부

    @Column(columnDefinition = "datetime(6)")
    private LocalDateTime arrivedAt;

    @Transient
    public ArrivalStatus getArrivalStatus() {
        if (!arrival) return ArrivalStatus.NOT_CONFIRMED;
        // Historical confirmations have no timestamp; preserve their existing treatment.
        if (arrivedAt == null) return ArrivalStatus.ON_TIME;
        return arrivedAt.isAfter(promise.getScheduledAt()) ? ArrivalStatus.LATE : ArrivalStatus.ON_TIME;
    }

    @Transient
    public boolean isOnTime() {
        return getArrivalStatus() == ArrivalStatus.ON_TIME;
    }
}
