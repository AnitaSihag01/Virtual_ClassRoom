package com.classroom.repository;
import com.classroom.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    List<Material> findByClassRoomIdOrderByUploadedAtDesc(Long classId);
    void deleteByClassRoomId(Long classId);
}
