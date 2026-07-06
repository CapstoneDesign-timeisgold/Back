package jiki.jiki.friend;

import jakarta.persistence.*;
import jiki.jiki.user.SiteUser;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(indexes = {
        @Index(name = "idx_friend_user1", columnList = "user_id1"),
        @Index(name = "idx_friend_user2", columnList = "user_id2"),
        @Index(name = "idx_friend_status", columnList = "status")
})
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id1")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private SiteUser user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id2")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private SiteUser user2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendStatus status;
}