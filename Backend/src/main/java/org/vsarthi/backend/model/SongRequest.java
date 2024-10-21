package org.vsarthi.backend.model;

public class SongRequest {
    private String youtubeLink;

    // Default constructor
    public SongRequest() {}

    // Getter and setter
    public String getYoutubeLink() {
        return youtubeLink;
    }

    public void setYoutubeLink(String youtubeLink) {
        this.youtubeLink = youtubeLink;
    }
}