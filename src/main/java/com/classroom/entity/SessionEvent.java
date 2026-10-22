package com.classroom.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "session_events", indexes = @Index(columnList = "session_id"))
@Getter @Setter @NoArgsConstructor
public class SessionEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false) private Long sessionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private EventType type;
    @Column(columnDefinition = "TEXT", nullable = false) private String data;
    @Column(name = "event_time", nullable = false) private Instant eventTime;
    public SessionEvent(Long sessionId, EventType type, String data, Instant eventTime) {
        this.sessionId = sessionId; this.type = type; this.data = data; this.eventTime = eventTime;
    }
}
