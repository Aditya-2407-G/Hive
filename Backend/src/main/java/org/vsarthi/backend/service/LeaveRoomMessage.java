package org.vsarthi.backend.service;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeaveRoomMessage {
    // getters and setters
    private String email;

    public void setEmail(String email) {
        this.email = email;
    }
}