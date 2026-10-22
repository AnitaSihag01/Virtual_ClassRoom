package com.classroom.dto;

import com.classroom.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** All request/response shapes in one place. */
public final class Dto {
    private Dto() {}

    // ---------- requests ----------
    public record LoginReq(@NotBlank @Email String email, @NotBlank String password, @NotNull Role role) {}
    public record CreateTeacherReq(@NotBlank String name, @NotBlank @Email String email,
                                   @NotBlank @Size(min = 6) String password) {}
    public record CreateStudentReq(@NotBlank String name, @NotBlank @Email String email,
                                   @NotBlank String enrollmentNumber, @NotBlank @Size(min = 6) String password) {}
    public record UpdateUserReq(@NotBlank String name, @NotBlank @Email String email, String enrollmentNumber) {}
    public record ResetPasswordReq(@NotBlank @Size(min = 6) String newPassword) {}
    public record ChangePasswordReq(@NotBlank String oldPassword, @NotBlank @Size(min = 6) String newPassword) {}
    public record ClassReq(@NotBlank String name, @NotBlank String subject, @NotBlank String semester, Long teacherId) {}

    // ---------- responses ----------
    public record UserView(Long id, String name, String email, String enrollmentNumber, Role role, boolean active) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getName(), u.getEmail(), u.getEnrollmentNumber(), u.getRole(), u.isActive());
        }
    }

    public record ClassView(Long id, String name, String subject, String semester, Long teacherId, String teacherName) {
        public static ClassView of(ClassRoom c) {
            User t = c.getTeacher();
            return new ClassView(c.getId(), c.getName(), c.getSubject(), c.getSemester(),
                    t == null ? null : t.getId(), t == null ? null : t.getName());
        }
    }

    public record SessionView(Long id, Long classId, String className, String teacherName,
                              Instant startedAt, Instant endedAt, boolean active) {
        public static SessionView of(LiveSession s) {
            User t = s.getClassRoom().getTeacher();
            return new SessionView(s.getId(), s.getClassRoom().getId(), s.getClassRoom().getName(),
                    t == null ? null : t.getName(), s.getStartedAt(), s.getEndedAt(), s.getEndedAt() == null);
        }
    }

    public record AttendanceView(Long sessionId, Long userId, String name, Instant joinedAt, Instant leftAt) {
        public static AttendanceView of(Attendance a) {
            return new AttendanceView(a.getSessionId(), a.getUser().getId(), a.getUser().getName(),
                    a.getJoinedAt(), a.getLeftAt());
        }
    }

    public record MaterialView(Long id, Long classId, String fileName, String uploadedBy, Instant uploadedAt) {
        public static MaterialView of(Material m) {
            return new MaterialView(m.getId(), m.getClassRoom().getId(), m.getFileName(),
                    m.getUploadedBy().getName(), m.getUploadedAt());
        }
    }

    public record LoginHistoryView(Long id, String email, String userName, Instant time, boolean success) {
        public static LoginHistoryView of(LoginHistory h) {
            return new LoginHistoryView(h.getId(), h.getEmail(), h.getUser() == null ? null : h.getUser().getName(),
                    h.getLoggedAt(), h.isSuccess());
        }
    }

    public record Participant(Long userId, String name, Role role) {}

    /** One saved stroke/chat event; offsetMs = time since session start (for replay). */
    public record EventView(Long id, EventType type, long offsetMs, JsonNode data) {}

    /** Everything a client needs when it joins late or reconnects. */
    public record StateView(Long sessionId, boolean active, List<JsonNode> strokes, List<JsonNode> chat,
                            List<Participant> participants, Long drawerId, List<Long> mutedIds, boolean youAreMuted) {}
}
