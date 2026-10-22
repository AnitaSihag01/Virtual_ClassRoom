package com.classroom.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "attendance", indexes = @Index(columnList = "session_id"))
@Getter @Setter @NoArgsConstructor
public class Attendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false) private Long sessionId;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false) private Instant joinedAt;
    private Instant leftAt;
    public Attendance(Long sessionId, User user, Instant joinedAt) {
        this.sessionId = sessionId; this.user = user; this.joinedAt = joinedAt;
    }
}
