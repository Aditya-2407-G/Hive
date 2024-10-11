package org.vsarthi.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;

@Service
public class YouTubeService {

    @Value("${youtube.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String getVideoTitle(String videoId) throws Exception {
        String url = String.format("https://www.googleapis.com/youtube/v3/videos?part=snippet&id=%s&key=%s", videoId, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JSONObject jsonResponse = new JSONObject(response.body());
        return jsonResponse.getJSONArray("items")
                .getJSONObject(0)
                .getJSONObject("snippet")
                .getString("title");
    }

    public String extractVideoId(String youtubeLink) {
        if (youtubeLink.contains("youtu.be/")) {
            return youtubeLink.split("youtu.be/")[1];
        } else if (youtubeLink.contains("v=")) {
            return youtubeLink.split("v=")[1].split("&")[0];
        }
        throw new IllegalArgumentException("Invalid YouTube link");
    }
}