package com.classroom.repository;
import com.classroom.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEnrollmentNumber(String enrollmentNumber);
    boolean existsByRole(Role role);
    long countByRole(Role role);
    List<User> findByRoleOrderByNameAsc(Role role);
}
