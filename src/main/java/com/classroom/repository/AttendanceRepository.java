package com.classroom.repository;
import com.classroom.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findBySessionIdOrderByJoinedAtAsc(Long sessionId);
    List<Attendance> findBySessionIdAndLeftAtIsNull(Long sessionId);
    Optional<Attendance> findFirstBySessionIdAndUserIdAndLeftAtIsNullOrderByIdDesc(Long sessionId, Long userId);
    List<Attendance> findByUserIdAndSessionIdInOrderByJoinedAtAsc(Long userId, Collection<Long> sessionIds);
    void deleteBySessionId(Long sessionId);
}
