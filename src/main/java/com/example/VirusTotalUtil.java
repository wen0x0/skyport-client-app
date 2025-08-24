package com.example;

import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Duration;
import org.json.JSONObject;

public class VirusTotalUtil {

    public static final String VT_API_KEY = System.getenv("VIRUS_TOTAL_API_KEY");

    /**
     * @param file File to be checked
     * @return true if file is safe (no malware), false if file is potentially harmful
     */
    public static boolean isFileSafe(File file) {
        if (VT_API_KEY == null || VT_API_KEY.isEmpty())
            return true;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();

            // 1. Upload file for analysis
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            String CRLF = "\r\n";
            byte[] fileBytes = Files.readAllBytes(file.toPath());

            // Build multipart body
            StringBuilder sb = new StringBuilder();
            sb.append("--").append(boundary).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
              .append(file.getName()).append("\"").append(CRLF);
            sb.append("Content-Type: application/octet-stream").append(CRLF).append(CRLF);

            byte[] preFile = sb.toString().getBytes();
            byte[] postFile = (CRLF + "--" + boundary + "--" + CRLF).getBytes();

            byte[] body = new byte[preFile.length + fileBytes.length + postFile.length];
            System.arraycopy(preFile, 0, body, 0, preFile.length);
            System.arraycopy(fileBytes, 0, body, preFile.length, fileBytes.length);
            System.arraycopy(postFile, 0, body, preFile.length + fileBytes.length, postFile.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.virustotal.com/api/v3/files"))
                    .header("x-apikey", VT_API_KEY)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            String analysisId = json.getJSONObject("data").getString("id");

            // 2. Wait and fetch analysis result
            // Note: In production, implement exponential backoff or webhook instead of fixed sleep
            Thread.sleep(8000);

            HttpRequest resultRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.virustotal.com/api/v3/analyses/" + analysisId))
                    .header("x-apikey", VT_API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> resultResponse = client.send(resultRequest, HttpResponse.BodyHandlers.ofString());
            JSONObject resultJson = new JSONObject(resultResponse.body());
            JSONObject stats = resultJson.getJSONObject("data").getJSONObject("attributes").getJSONObject("stats");
            int malicious = stats.getInt("malicious");
            int suspicious = stats.getInt("suspicious");

            return (malicious == 0 && suspicious == 0);
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }
}