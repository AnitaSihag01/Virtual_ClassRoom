package com.classroom.repository;
import com.classroom.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {
    List<LiveSession> findByClassRoomIdOrderByStartedAtDesc(Long classId);
    Optional<LiveSession> findFirstByClassRoomIdAndEndedAtIsNull(Long classId);
    List<LiveSession> findAllByOrderByStartedAtDesc();
    List<LiveSession> findByEndedAtIsNull();
    void deleteByClassRoomId(Long classId);
}
