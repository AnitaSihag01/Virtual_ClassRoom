package com.classroom.repository;
import com.classroom.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface SessionEventRepository extends JpaRepository<SessionEvent, Long> {
    List<SessionEvent> findBySessionIdOrderByIdAsc(Long sessionId);
    List<SessionEvent> findBySessionIdAndTypeOrderByIdAsc(Long sessionId, EventType type);
    void deleteBySessionId(Long sessionId);
}
