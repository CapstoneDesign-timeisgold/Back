package jiki.jiki.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Set;

@Data
@Entity
public class SiteUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    private String password;

    @Column(unique = true)
    private String nickname;

    @Column(unique = true)
    private String email;

    private int money;

    @OneToMany(mappedBy = "user1")
    private Set<FriendShip> friendshipsInitiated;

    @OneToMany(mappedBy = "user2")
    private Set<FriendShip> friendshipsReceived;

    @OneToMany(mappedBy = "user")
    private Set<Participant> participations;

}
