package com.classroom.service;

import com.classroom.dto.Dto.Participant;
import org.springframework.stereotype.Component;

import java.util.*;

/** In-memory live state of each running room (who is online, who may draw, muted, removed). No messaging here. */
@Component
public class RoomRegistry {
    public record LeaveResult(Long sessionId, Participant participant, boolean lastConnection) {}

    private static class Room {
        Long teacherId;
        final Map<String, Participant> byWs = new HashMap<>();
        Long drawerId;
        final Set<Long> muted = new HashSet<>();
        final Set<Long> kicked = new HashSet<>();
    }

    private final Map<Long, Room> rooms = new HashMap<>();

    private Room room(Long sid) { return rooms.computeIfAbsent(sid, k -> new Room()); }

    /** @return true if this is the user's first open connection in the room. */
    public synchronized boolean join(Long sid, Long teacherId, String ws, Participant p) {
        Room r = room(sid);
        r.teacherId = teacherId;
        boolean first = r.byWs.values().stream().noneMatch(x -> x.userId().equals(p.userId()));
        r.byWs.put(ws, p);
        return first;
    }

    public synchronized List<LeaveResult> leaveWs(String ws) {
        List<LeaveResult> out = new ArrayList<>();
        for (Map.Entry<Long, Room> e : rooms.entrySet()) {
            Participant p = e.getValue().byWs.remove(ws);
            if (p != null) {
                boolean last = e.getValue().byWs.values().stream().noneMatch(x -> x.userId().equals(p.userId()));
                out.add(new LeaveResult(e.getKey(), p, last));
            }
        }
        return out;
    }

    public synchronized List<Participant> participants(Long sid) {
        Room r = rooms.get(sid);
        if (r == null) return List.of();
        Map<Long, Participant> m = new LinkedHashMap<>();
        r.byWs.values().forEach(p -> m.putIfAbsent(p.userId(), p));
        return new ArrayList<>(m.values());
    }

    public synchronized boolean isMember(Long sid, Long uid) {
        Room r = rooms.get(sid);
        return r != null && r.byWs.values().stream().anyMatch(p -> p.userId().equals(uid));
    }

    public synchronized boolean isTeacher(Long sid, Long uid) {
        Room r = rooms.get(sid);
        return r != null && uid.equals(r.teacherId);
    }

    public synchronized Long drawerId(Long sid) { Room r = rooms.get(sid); return r == null ? null : r.drawerId; }
    public synchronized void setDrawer(Long sid, Long uid) { room(sid).drawerId = uid; }

    public synchronized boolean isMuted(Long sid, Long uid) { Room r = rooms.get(sid); return r != null && r.muted.contains(uid); }
    public synchronized void setMuted(Long sid, Long uid, boolean muted) {
        if (muted) room(sid).muted.add(uid); else room(sid).muted.remove(uid);
    }
    public synchronized List<Long> mutedIds(Long sid) { Room r = rooms.get(sid); return r == null ? List.of() : new ArrayList<>(r.muted); }

    public synchronized boolean isKicked(Long sid, Long uid) { Room r = rooms.get(sid); return r != null && r.kicked.contains(uid); }

    /** @return true if the user was connected. */
    public synchronized boolean kick(Long sid, Long uid) {
        Room r = room(sid);
        r.kicked.add(uid);
        if (uid.equals(r.drawerId)) r.drawerId = null;
        return r.byWs.values().removeIf(p -> p.userId().equals(uid));
    }

    public synchronized void close(Long sid) { rooms.remove(sid); }
}
