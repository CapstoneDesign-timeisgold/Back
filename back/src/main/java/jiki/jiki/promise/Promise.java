package jiki.jiki.promise;

import jakarta.persistence.*;
import jiki.jiki.user.SiteUser;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashSet;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Entity
public class Promise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String date;
    private String time;
    // null인 기존 약속은 시작 시각을 참여 마감으로 사용한다.
    private LocalDateTime participationDeadline;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean participationDeadlineLocked;
    private int penalty;
    private String title;
    private double latitude;
    private double longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private SiteUser creator;  // 약속 생성자

    @OneToMany(mappedBy = "promise", cascade = CascadeType.ALL, orphanRemoval = true)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<Participant> participants = new HashSet<>();

    private boolean isSettled = false;

    // Immutable response of newly finalized settlements; null for legacy settlements.
    @Column(columnDefinition = "TEXT")
    private String settlementResult;
}
