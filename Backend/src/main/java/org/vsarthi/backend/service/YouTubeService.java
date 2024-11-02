package org.vsarthi.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class YouTubeService {

    @Value("${youtube.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String getVideoTitle(String videoId) throws Exception {
        JSONObject videoInfo = getVideoInfo(videoId, "snippet");
        return videoInfo.getJSONObject("snippet").getString("title");
    }

    public boolean isVideoAvailable(String videoId) throws Exception {
        JSONObject videoInfo = getVideoInfo(videoId, "status");
        String privacyStatus = videoInfo.getJSONObject("status").getString("privacyStatus");
        boolean embeddable = videoInfo.getJSONObject("status").getBoolean("embeddable");

        return "public".equals(privacyStatus) && embeddable;
    }

    public String extractVideoId(String youtubeLink) {
        if (youtubeLink.contains("youtu.be/")) {
            return youtubeLink.split("youtu.be/")[1];
        } else if (youtubeLink.contains("v=")) {
            return youtubeLink.split("v=")[1].split("&")[0];
        }
        throw new IllegalArgumentException("Invalid YouTube link");
    }

    private JSONObject getVideoInfo(String videoId, String part) throws Exception {
        String url = String.format("https://www.googleapis.com/youtube/v3/videos?part=%s&id=%s&key=%s", part, videoId, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to fetch video info. Status code: " + response.statusCode());
        }

        JSONObject jsonResponse = new JSONObject(response.body());
        JSONArray items = jsonResponse.getJSONArray("items");

        if (items.isEmpty()) {
            throw new Exception("Video not found");
        }

        return items.getJSONObject(0);
    }

}