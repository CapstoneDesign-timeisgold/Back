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
    @JoinColumn(name = "user_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private SiteUser user;  // 사용자 정보

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ParticipantStatus status;  // 참여 상태

    private boolean isLate;  // 지각 여부
}