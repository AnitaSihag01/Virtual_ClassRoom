package com.classroom.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "classes") @Getter @Setter @NoArgsConstructor
public class ClassRoom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String subject;
    @Column(nullable = false) private String semester;
    @ManyToOne @JoinColumn(name = "teacher_id") private User teacher;
}
