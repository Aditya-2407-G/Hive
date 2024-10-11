package org.vsarthi.backend.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.vsarthi.backend.model.UserPresence;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.service.RoomService;

@Component
public class WebSocketEventListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final RoomService roomService;

    public WebSocketEventListener(SimpMessageSendingOperations messagingTemplate, RoomService roomService) {
        this.messagingTemplate = messagingTemplate;
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
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) headers.getUser();

        if (authentication != null && authentication.getPrincipal() instanceof Users user) {
            System.out.println("User disconnected: " + user.getUsername() + " with session: " + sessionId);

            // Handle user disconnection from rooms
            roomService.handleUserDisconnection(user, sessionId);
        }
    }
}