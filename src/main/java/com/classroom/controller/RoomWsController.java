package com.classroom.controller;

import com.classroom.exception.ApiException;
import com.classroom.security.AppUserDetails;
import com.classroom.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP protocol (client -> server, prefix /app):
 *   /app/room/{id}/join   (no body)        /app/room/{id}/leave (no body)
 *   /app/room/{id}/draw   {action:"draw"|"clear", strokeId, color, width, eraser, points:[[x,y],...]}
 *   /app/room/{id}/chat   {text}
 * Server -> clients:  /topic/room.{id}.draw | chat | presence | control,  and personal errors at /user/queue/errors
 */
@Controller
@RequiredArgsConstructor
public class RoomWsController {
    private final RoomService rooms;

    private AppUserDetails user(Principal p) { return (AppUserDetails) ((Authentication) p).getPrincipal(); }

    @MessageMapping("/room/{sessionId}/join")
    public void join(@DestinationVariable Long sessionId, SimpMessageHeaderAccessor h, Principal p) {
        rooms.join(sessionId, h.getSessionId(), user(p));
    }

    @MessageMapping("/room/{sessionId}/leave")
    public void leave(@DestinationVariable Long sessionId, SimpMessageHeaderAccessor h) {
        rooms.leave(h.getSessionId());
    }

    @MessageMapping("/room/{sessionId}/draw")
    public void draw(@DestinationVariable Long sessionId, @Payload Map<String, Object> payload, Principal p) {
        rooms.handleDraw(sessionId, user(p), payload);
    }

    @MessageMapping("/room/{sessionId}/chat")
    public void chat(@DestinationVariable Long sessionId, @Payload Map<String, Object> payload, Principal p) {
        rooms.handleChat(sessionId, user(p), payload);
    }

    // video call setup messages (WebRTC offer / answer / ice candidate)
    @MessageMapping("/room/{sessionId}/signal")
    public void signal(@DestinationVariable Long sessionId, @Payload Map<String, Object> payload, Principal p) {
        rooms.handleSignal(sessionId, user(p), payload);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, String> onError(Exception e) {
        return Map.of("message", e instanceof ApiException ? e.getMessage() : "Request failed");
    }
}
