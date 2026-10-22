package com.classroom.service;

import com.classroom.entity.*;
import com.classroom.exception.ApiException;
import com.classroom.repository.*;
import com.classroom.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** SYS-7: central class-membership checks used by REST controllers and the WebSocket interceptor. */
@Service
@RequiredArgsConstructor
public class AccessService {
    private final UserRepository users;
    private final ClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final LiveSessionRepository sessions;

    private ApiException denied() { return new ApiException(HttpStatus.FORBIDDEN, "Access denied"); }

    /** ADM-13: re-check the database so a deactivated user is cut off immediately. */
    public void ensureActive(AppUserDetails u) {
        if (!users.findById(u.getId()).map(User::isActive).orElse(false)) throw denied();
    }

    public boolean isMember(AppUserDetails u, ClassRoom c) {
        if (u.getRole() == Role.TEACHER)
            return c.getTeacher() != null && c.getTeacher().getId().equals(u.getId());
        if (u.getRole() == Role.STUDENT)
            return enrollments.existsByClassRoomIdAndStudentId(c.getId(), u.getId());
        return false;   // admin has no access to rooms or materials
    }

    public ClassRoom requireClassMember(AppUserDetails u, Long classId) {
        ensureActive(u);
        ClassRoom c = classes.findById(classId).orElseThrow(this::denied);
        if (!isMember(u, c)) throw denied();
        return c;
    }

    public ClassRoom requireClassTeacher(AppUserDetails u, Long classId) {
        ClassRoom c = requireClassMember(u, classId);
        if (u.getRole() != Role.TEACHER) throw denied();
        return c;
    }

    public LiveSession requireSessionMember(AppUserDetails u, Long sessionId) {
        ensureActive(u);
        LiveSession s = sessions.findById(sessionId).orElseThrow(this::denied);
        if (!isMember(u, s.getClassRoom())) throw denied();
        return s;
    }

    public LiveSession requireSessionTeacher(AppUserDetails u, Long sessionId) {
        LiveSession s = requireSessionMember(u, sessionId);
        if (u.getRole() != Role.TEACHER) throw denied();
        return s;
    }

    /** For WebSocket: member AND the session is still running. */
    public LiveSession requireLiveSessionMember(AppUserDetails u, Long sessionId) {
        LiveSession s = requireSessionMember(u, sessionId);
        if (s.getEndedAt() != null) throw denied();
        return s;
    }
}
