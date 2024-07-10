package jiki.jiki.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
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
}