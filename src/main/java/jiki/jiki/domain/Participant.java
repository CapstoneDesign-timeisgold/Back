package jiki.jiki.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "promise_id")
    private Promise promise;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private SiteUser user;

    private boolean isLate;

    // Getters and Setters
}
