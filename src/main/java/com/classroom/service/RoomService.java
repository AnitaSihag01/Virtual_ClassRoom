package com.classroom.service;

import com.classroom.dto.Dto.Participant;
import com.classroom.entity.*;
import com.classroom.exception.ApiException;
import com.classroom.repository.*;
import com.classroom.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.*;

/** Live room logic: join/leave (attendance), drawing, chat, and teacher controls. */
@Service
@RequiredArgsConstructor
public class RoomService {
    private final SimpMessagingTemplate template;
    private final RoomRegistry registry;
    private final AttendanceRepository attendance;
    private final UserRepository users;
    private final EnrollmentRepository enrollments;
    private final LiveSessionRepository sessions;
    private final EventService events;

    private static String topic(Long sid, String channel) { return "/topic/room." + sid + "." + channel; }

    /** Payload typed as Object avoids the ambiguous convertAndSend(Object, Map headers) overload. */
    private void send(String destination, Object payload) {
        template.convertAndSend(destination, payload);
    }

    // ---------- join / leave (SYS-19) ----------
    public void join(Long sid, String wsId, AppUserDetails u) {
        LiveSession s = sessions.findById(sid).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Access denied"));
        if (s.getEndedAt() != null) throw new ApiException(HttpStatus.FORBIDDEN, "Session has ended");
        if (registry.isKicked(sid, u.getId())) throw new ApiException(HttpStatus.FORBIDDEN, "You were removed from this session");
        Long teacherId = s.getClassRoom().getTeacher().getId();
        boolean first = registry.join(sid, teacherId, wsId, new Participant(u.getId(), u.getName(), u.getRole()));
        if (first) attendance.save(new Attendance(sid, users.getReferenceById(u.getId()), Instant.now()));
        broadcastPresence(sid);
    }

    public void leave(String wsId) { handleLeaves(registry.leaveWs(wsId)); }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) { leave(e.getSessionId()); }

    private void handleLeaves(List<RoomRegistry.LeaveResult> results) {
        for (RoomRegistry.LeaveResult r : results) {
            if (r.lastConnection()) closeAttendance(r.sessionId(), r.participant().userId());
            broadcastPresence(r.sessionId());
        }
    }

    private void closeAttendance(Long sid, Long userId) {
        attendance.findFirstBySessionIdAndUserIdAndLeftAtIsNullOrderByIdDesc(sid, userId).ifPresent(a -> {
            a.setLeftAt(Instant.now());
            attendance.save(a);
        });
    }

    private void broadcastPresence(Long sid) {
        send(topic(sid, "presence"), Map.of("participants", registry.participants(sid)));
    }

    // ---------- whiteboard (SYS-10, SYS-11, SYS-12, SYS-16) ----------
    public void handleDraw(Long sid, AppUserDetails u, Map<String, Object> in) {
        if (!registry.isMember(sid, u.getId())) throw new ApiException(HttpStatus.FORBIDDEN, "Join the room first");
        boolean teacher = registry.isTeacher(sid, u.getId());
        Object action = in.get("action");
        if ("clear".equals(action)) {
            if (!teacher) throw new ApiException(HttpStatus.FORBIDDEN, "Only the teacher can clear the board");
        } else if ("draw".equals(action)) {
            if (!teacher && !u.getId().equals(registry.drawerId(sid)))
                throw new ApiException(HttpStatus.FORBIDDEN, "You do not have drawing permission");
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown action");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", action);
        out.put("senderId", u.getId());
        if ("draw".equals(action)) {
            String color = String.valueOf(in.getOrDefault("color", "#000000"));
            if (!color.matches("#[0-9a-fA-F]{6}")) color = "#000000";
            int width = in.get("width") instanceof Number n ? Math.max(1, Math.min(50, n.intValue())) : 3;
            String strokeId = String.valueOf(in.getOrDefault("strokeId", ""));
            if (strokeId.length() > 64) strokeId = strokeId.substring(0, 64);
            List<List<Double>> pts = new ArrayList<>();
            if (in.get("points") instanceof List<?> list) {
                if (list.size() > 500) throw new ApiException(HttpStatus.BAD_REQUEST, "Too many points in one message");
                for (Object o : list) {
                    if (o instanceof List<?> xy && xy.size() == 2
                            && xy.get(0) instanceof Number x && xy.get(1) instanceof Number y)
                        pts.add(List.of(x.doubleValue(), y.doubleValue()));
                }
            }
            if (pts.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "No points");
            out.put("strokeId", strokeId);
            out.put("color", color);
            out.put("width", width);
            out.put("eraser", Boolean.TRUE.equals(in.get("eraser")));
            out.put("points", pts);
        }
        events.save(sid, EventType.STROKE, out);
        send(topic(sid, "draw"), out);
    }

    // ---------- chat (SYS-14, SYS-15) ----------
    public void handleChat(Long sid, AppUserDetails u, Map<String, Object> in) {
        if (!registry.isMember(sid, u.getId())) throw new ApiException(HttpStatus.FORBIDDEN, "Join the room first");
        if (registry.isMuted(sid, u.getId())) throw new ApiException(HttpStatus.FORBIDDEN, "You are muted by the teacher");
        String text = String.valueOf(in.getOrDefault("text", "")).trim();
        if (text.isEmpty() || text.length() > 1000)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Message must be 1-1000 characters");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", "message");
        out.put("senderId", u.getId());
        out.put("senderName", u.getName());
        out.put("role", u.getRole().name());
        out.put("text", text);
        out.put("time", Instant.now().toString());
        events.save(sid, EventType.CHAT, out);
        send(topic(sid, "chat"), out);
    }

    // ---------- live video (WebRTC signaling) ----------
    // The video itself goes directly browser to browser. The server only passes the setup messages.
    // Teacher sends video to students. Students can only send messages to the teacher.
    private static final Set<String> SIGNAL_TYPES = Set.of("hello", "ready", "offer", "answer", "candidate");

    public void handleSignal(Long sid, AppUserDetails u, Map<String, Object> in) {
        if (!registry.isMember(sid, u.getId())) throw new ApiException(HttpStatus.FORBIDDEN, "Join the room first");
        String type = String.valueOf(in.get("type"));
        if (!SIGNAL_TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown signal");
        Object data = in.get("data");
        if (data != null && String.valueOf(data).length() > 20000) throw new ApiException(HttpStatus.BAD_REQUEST, "Signal too big");
        String cid = String.valueOf(in.getOrDefault("cid", ""));
        if (cid.length() > 32) cid = cid.substring(0, 32);

        boolean teacher = registry.isTeacher(sid, u.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", sid);
        out.put("from", u.getId());
        out.put("type", type);
        out.put("cid", cid);
        out.put("data", data);

        if ("ready".equals(type)) {
            // teacher says "I am here" to every student, students then reply with hello
            if (!teacher) throw new ApiException(HttpStatus.FORBIDDEN, "Only the teacher can do this");
            for (Participant p : registry.participants(sid)) {
                if (!p.userId().equals(u.getId())) sendToUser(p.userId(), out);
            }
            return;
        }

        if (!(in.get("to") instanceof Number n)) throw new ApiException(HttpStatus.BAD_REQUEST, "Missing target");
        Long to = n.longValue();
        if (!teacher && !registry.isTeacher(sid, to)) throw new ApiException(HttpStatus.FORBIDDEN, "Students can only signal the teacher");
        if (!registry.isMember(sid, to)) return;   // other person already left, nothing to do
        sendToUser(to, out);
    }

    private void sendToUser(Long userId, Object payload) {
        users.findById(userId).ifPresent(x -> template.convertAndSendToUser(x.getEmail(), "/queue/signal", payload));
    }

    // ---------- teacher controls ----------
    private void requireEnrolled(LiveSession s, Long studentId) {
        if (!enrollments.existsByClassRoomIdAndStudentId(s.getClassRoom().getId(), studentId))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Student is not enrolled in this class");
    }

    /** TCH-6: only one student can hold drawing permission at a time; null takes it back. */
    public void setDrawing(LiveSession s, Long studentIdOrNull) {
        if (studentIdOrNull != null) requireEnrolled(s, studentIdOrNull);
        registry.setDrawer(s.getId(), studentIdOrNull);
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "drawing");
        msg.put("userId", studentIdOrNull);
        send(topic(s.getId(), "control"), msg);
    }

    public void setMuted(LiveSession s, Long studentId, boolean muted) {
        requireEnrolled(s, studentId);
        registry.setMuted(s.getId(), studentId, muted);
        send(topic(s.getId(), "control"), Map.of("type", "muted", "userId", studentId, "muted", muted));
    }

    public void clearChat(LiveSession s) {
        events.save(s.getId(), EventType.CHAT, Map.of("action", "clear"));
        send(topic(s.getId(), "chat"), Map.of("action", "clear"));
    }

    /** TCH-10: remove a student; they cannot re-join this session. */
    public void kick(LiveSession s, Long studentId) {
        requireEnrolled(s, studentId);
        send(topic(s.getId(), "control"), Map.of("type", "kicked", "userId", studentId));
        registry.kick(s.getId(), studentId);
        closeAttendance(s.getId(), studentId);
        broadcastPresence(s.getId());
    }

    /** TCH-4: room closes for everyone. */
    public void endSession(Long sid) {
        send(topic(sid, "control"), Map.of("type", "ended"));
        Instant now = Instant.now();
        attendance.findBySessionIdAndLeftAtIsNull(sid).forEach(a -> { a.setLeftAt(now); attendance.save(a); });
        registry.close(sid);
    }
}
