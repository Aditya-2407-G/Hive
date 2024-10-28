package org.vsarthi.backend.DTO;

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
