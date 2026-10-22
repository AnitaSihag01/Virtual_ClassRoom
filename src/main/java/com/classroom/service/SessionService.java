package com.classroom.service;

import com.classroom.dto.Dto.*;
import com.classroom.entity.*;
import com.classroom.exception.ApiException;
import com.classroom.repository.*;
import com.classroom.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final LiveSessionRepository sessions;
    private final AccessService access;
    private final RoomService rooms;
    private final RoomRegistry registry;
    private final EventService events;
    private final AttendanceRepository attendance;
    private final EnrollmentRepository enrollments;

    /** TCH-3 */
    public SessionView start(AppUserDetails me, Long classId) {
        ClassRoom c = access.requireClassTeacher(me, classId);
        LiveSession s = sessions.findFirstByClassRoomIdAndEndedAtIsNull(classId).orElseGet(() -> {
            LiveSession n = new LiveSession();
            n.setClassRoom(c);
            n.setStartedAt(Instant.now());
            return sessions.save(n);
        });
        return SessionView.of(s);
    }

    /** TCH-4 */
    public SessionView end(AppUserDetails me, Long sessionId) {
        LiveSession s = access.requireSessionTeacher(me, sessionId);
        if (s.getEndedAt() == null) {
            s.setEndedAt(Instant.now());          // set first so nobody can join any more
            sessions.save(s);
            rooms.endSession(sessionId);
        }
        return SessionView.of(s);
    }

    public List<SessionView> classSessions(AppUserDetails me, Long classId) {
        access.requireClassMember(me, classId);
        return sessions.findByClassRoomIdOrderByStartedAtDesc(classId).stream().map(SessionView::of).toList();
    }

    /** STD-3: students only see a session to join once the teacher started it. */
    public Optional<SessionView> active(AppUserDetails me, Long classId) {
        access.requireClassMember(me, classId);
        return sessions.findFirstByClassRoomIdAndEndedAtIsNull(classId).map(SessionView::of);
    }

    /** STD-4, SYS-13, STD-12: current board + chat + people; call after join and after every reconnect. */
    public StateView state(AppUserDetails me, Long sessionId) {
        LiveSession s = access.requireSessionMember(me, sessionId);
        return new StateView(sessionId, s.getEndedAt() == null,
                events.boardStrokes(sessionId), events.chatHistory(sessionId),
                registry.participants(sessionId), registry.drawerId(sessionId),
                registry.mutedIds(sessionId), registry.isMuted(sessionId, me.getId()));
    }

    /** TCH-14, STD-10, SYS-17 */
    public List<EventView> replay(AppUserDetails me, Long sessionId) {
        return events.replay(access.requireSessionMember(me, sessionId));
    }

    public List<Participant> participants(AppUserDetails me, Long sessionId) {
        access.requireSessionMember(me, sessionId);
        return registry.participants(sessionId);
    }

    /** TCH-13 */
    public List<AttendanceView> attendanceForTeacher(AppUserDetails me, Long sessionId) {
        access.requireSessionTeacher(me, sessionId);
        return attendance.findBySessionIdOrderByJoinedAtAsc(sessionId).stream()
                .filter(a -> a.getUser().getRole() == Role.STUDENT).map(AttendanceView::of).toList();
    }

    /** STD-11: only the student's own rows. */
    public List<AttendanceView> myAttendance(AppUserDetails me, Long classId) {
        List<Long> classIds = new ArrayList<>();
        if (classId != null) {
            classIds.add(access.requireClassMember(me, classId).getId());
        } else {
            access.ensureActive(me);
            enrollments.findByStudentId(me.getId()).forEach(e -> classIds.add(e.getClassRoom().getId()));
        }
        List<Long> sessionIds = new ArrayList<>();
        classIds.forEach(id -> sessions.findByClassRoomIdOrderByStartedAtDesc(id).forEach(s -> sessionIds.add(s.getId())));
        if (sessionIds.isEmpty()) return List.of();
        return attendance.findByUserIdAndSessionIdInOrderByJoinedAtAsc(me.getId(), sessionIds)
                .stream().map(AttendanceView::of).toList();
    }

    /** ADM-10 */
    public List<SessionView> all() { return sessions.findAllByOrderByStartedAtDesc().stream().map(SessionView::of).toList(); }

    // used by the teacher controls
    public LiveSession teacherSession(AppUserDetails me, Long sessionId) {
        LiveSession s = access.requireSessionTeacher(me, sessionId);
        if (s.getEndedAt() != null) throw new ApiException(HttpStatus.CONFLICT, "Session has ended");
        return s;
    }
}
