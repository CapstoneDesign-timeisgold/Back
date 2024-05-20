package jiki.jiki.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class FriendShip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id1")
    private SiteUser user1;

    @ManyToOne
    @JoinColumn(name = "user_id2")
    private SiteUser user2;

    @Enumerated(EnumType.STRING)
    private FriendShipStatus status;

}
