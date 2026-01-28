package jiki.jiki.friend;

import jakarta.persistence.*;
import jiki.jiki.user.SiteUser;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
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