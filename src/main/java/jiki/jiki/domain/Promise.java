package jiki.jiki.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Entity
public class Promise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dateTime;

    private String location;

    private int penalty;

    private String title;

    @OneToMany(mappedBy = "promise")
    private Set<Participant> participants;

    // Getters and Setters
}
