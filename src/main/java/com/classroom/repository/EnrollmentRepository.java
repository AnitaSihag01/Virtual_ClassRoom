package com.classroom.repository;
import com.classroom.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    boolean existsByClassRoomIdAndStudentId(Long classId, Long studentId);
    List<Enrollment> findByClassRoomId(Long classId);
    List<Enrollment> findByStudentId(Long studentId);
    void deleteByClassRoomIdAndStudentId(Long classId, Long studentId);
    void deleteByClassRoomId(Long classId);
}
