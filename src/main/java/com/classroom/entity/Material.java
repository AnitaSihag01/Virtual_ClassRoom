package com.classroom.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "materials") @Getter @Setter @NoArgsConstructor
public class Material {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "class_id") private ClassRoom classRoom;
    @Column(nullable = false) private String fileName;
    @Column(nullable = false) private String filePath;
    @ManyToOne(optional = false) @JoinColumn(name = "uploaded_by") private User uploadedBy;
    @Column(nullable = false) private Instant uploadedAt;
}
