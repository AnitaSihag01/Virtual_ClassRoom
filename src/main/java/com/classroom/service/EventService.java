package com.classroom.service;

import com.classroom.dto.Dto.EventView;
import com.classroom.entity.EventType;
import com.classroom.entity.LiveSession;
import com.classroom.entity.SessionEvent;
import com.classroom.repository.SessionEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** SYS-16 / SYS-13 / SYS-17: persists strokes and chat, and reads them back. */
@Service
@RequiredArgsConstructor
public class EventService {
    private final SessionEventRepository repo;
    private final ObjectMapper om;

    public String toJson(Object o) {
        try { return om.writeValueAsString(o); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    public JsonNode parse(String s) {
        try { return om.readTree(s); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    public void save(Long sessionId, EventType type, Object data) {
        repo.save(new SessionEvent(sessionId, type, toJson(data), Instant.now()));
    }

    /** Strokes needed to rebuild the board now: everything after the last "clear". */
    public List<JsonNode> boardStrokes(Long sessionId) {
        return afterLastClear(repo.findBySessionIdAndTypeOrderByIdAsc(sessionId, EventType.STROKE));
    }

    public List<JsonNode> chatHistory(Long sessionId) {
        return afterLastClear(repo.findBySessionIdAndTypeOrderByIdAsc(sessionId, EventType.CHAT));
    }

    private List<JsonNode> afterLastClear(List<SessionEvent> events) {
        List<JsonNode> nodes = new ArrayList<>();
        for (SessionEvent e : events) {
            JsonNode n = parse(e.getData());
            if ("clear".equals(n.path("action").asText())) nodes.clear(); else nodes.add(n);
        }
        return nodes;
    }

    /** Full ordered timeline for replay (strokes + chat, including clear actions). */
    public List<EventView> replay(LiveSession s) {
        List<EventView> out = new ArrayList<>();
        for (SessionEvent e : repo.findBySessionIdOrderByIdAsc(s.getId())) {
            long offset = Math.max(0, Duration.between(s.getStartedAt(), e.getEventTime()).toMillis());
            out.add(new EventView(e.getId(), e.getType(), offset, parse(e.getData())));
        }
        return out;
    }
}
