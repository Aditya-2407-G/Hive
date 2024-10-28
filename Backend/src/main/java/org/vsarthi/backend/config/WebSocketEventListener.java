package org.vsarthi.backend.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.service.RoomService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketEventListener {

    private final RoomService roomService;

    public WebSocketEventListener( RoomService roomService) {
        this.roomService = roomService;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) headers.getUser();

        if (authentication != null && authentication.getPrincipal() instanceof Users user) {
            System.out.println("User connected: " + user.getUsername() + " with session: " + sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        roomService.handleUserDisconnection(sessionId);
    }


}