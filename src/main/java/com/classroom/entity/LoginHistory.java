package com.classroom.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "login_history") @Getter @Setter @NoArgsConstructor
public class LoginHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name = "user_id") private User user;
    private String email;
    @Column(name = "logged_at", nullable = false) private Instant loggedAt;
    private boolean success;
    public LoginHistory(User user, String email, Instant loggedAt, boolean success) {
        this.user = user; this.email = email; this.loggedAt = loggedAt; this.success = success;
    }
}
