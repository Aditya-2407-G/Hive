package org.vsarthi.backend.DTO;

import lombok.Data;

@Data
public class PlaylistRequest {
    private String name;
    private String description;
    private String genre;
}