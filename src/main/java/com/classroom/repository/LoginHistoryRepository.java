package com.classroom.repository;
import com.classroom.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    List<LoginHistory> findAllByOrderByLoggedAtDesc(Pageable pageable);
}
