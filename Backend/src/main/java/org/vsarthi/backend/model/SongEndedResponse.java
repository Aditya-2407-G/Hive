package org.vsarthi.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SongEndedResponse {

    private Long endedSongId;
    private List<Long> newSongOrder;

    public SongEndedResponse(Long endedSongId, List<Long> newSongOrder) {
        this.endedSongId = endedSongId;
        this.newSongOrder = newSongOrder;
    }
}
