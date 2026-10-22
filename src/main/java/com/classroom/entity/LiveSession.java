package com.classroom.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "sessions") @Getter @Setter @NoArgsConstructor
public class LiveSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "class_id") private ClassRoom classRoom;
    @Column(nullable = false) private Instant startedAt;
    private Instant endedAt;
}
