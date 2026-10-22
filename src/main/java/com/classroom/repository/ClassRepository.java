package com.classroom.repository;
import com.classroom.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface ClassRepository extends JpaRepository<ClassRoom, Long> {
    List<ClassRoom> findByTeacherId(Long teacherId);
}
