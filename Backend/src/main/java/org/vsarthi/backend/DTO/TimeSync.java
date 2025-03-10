package org.vsarthi.backend.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimeSync {
    private double currentTime;
    private boolean isPlaying;
}
