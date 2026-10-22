package com.classroom.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "enrollment", uniqueConstraints = @UniqueConstraint(columnNames = {"class_id", "student_id"}))
@Getter @Setter @NoArgsConstructor
public class Enrollment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "class_id") private ClassRoom classRoom;
    @ManyToOne(optional = false) @JoinColumn(name = "student_id") private User student;
    public Enrollment(ClassRoom c, User s) { this.classRoom = c; this.student = s; }
}
