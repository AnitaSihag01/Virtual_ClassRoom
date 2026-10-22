package com.classroom.security;

import com.classroom.service.AccessService;
import com.classroom.service.RoomRegistry;
import com.classroom.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SYS-8: a user cannot join a room by connecting directly.
 * Every SUBSCRIBE and SEND is checked: logged in, active, member of the class, session running, not removed.
 */
@Component
@RequiredArgsConstructor
public class StompSecurityInterceptor implements ChannelInterceptor {
    private static final Pattern SUB = Pattern.compile("^/topic/room\\.(\\d+)\\.(draw|chat|presence|control)$");
    private static final Pattern SEND = Pattern.compile("^/app/room/(\\d+)/(join|leave|draw|chat|signal)$");

    private final AccessService access;
    private final RoomRegistry registry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) return message;
        StompCommand cmd = acc.getCommand();
        if (cmd != StompCommand.CONNECT && cmd != StompCommand.SUBSCRIBE && cmd != StompCommand.SEND) return message;

        AppUserDetails u = userOf(acc);
        if (u == null) throw new MessageDeliveryException("Not authenticated");
        if (cmd == StompCommand.CONNECT) return message;

        String dest = acc.getDestination() == null ? "" : acc.getDestination();
        if (cmd == StompCommand.SUBSCRIBE && dest.startsWith("/user/queue/")) return message;   // personal error queue

        Matcher m = (cmd == StompCommand.SUBSCRIBE ? SUB : SEND).matcher(dest);
        if (!m.matches()) throw new MessageDeliveryException("Destination not allowed");
        Long sessionId = Long.valueOf(m.group(1));
        try {
            access.requireLiveSessionMember(u, sessionId);
        } catch (ApiException e) {
            throw new MessageDeliveryException("Access denied");
        }
        if (registry.isKicked(sessionId, u.getId())) throw new MessageDeliveryException("Removed from session");
        return message;
    }

    private AppUserDetails userOf(StompHeaderAccessor acc) {
        Principal p = acc.getUser();
        if (p instanceof Authentication a && a.getPrincipal() instanceof AppUserDetails d) return d;
        return null;
    }
}
