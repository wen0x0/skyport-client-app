package com.example;

import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Duration;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cdimascio.dotenv.Dotenv;

public class VirusTotalUtil {

    private static final Logger logger = LoggerFactory.getLogger(VirusTotalUtil.class);
    private static final Dotenv dotenv = Dotenv.load();
    public static final String VT_API_KEY = dotenv.get("VIRUS_TOTAL_API_KEY");

    /**
     * @param file File to be checked
     * @return true if file is safe (no malware), false if file is potentially harmful
     */
    public static boolean isFileSafe(File file) {
        if (VT_API_KEY == null || VT_API_KEY.isEmpty()) {
            logger.warn("VirusTotal API key is not set. Skipping malware check for file: {}", file.getName());
            return true;
        }
        try {
            logger.info("Checking file with VirusTotal: {}", file.getAbsolutePath());
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            // 1. If file > 32MB, get upload_url
            String uploadUrl = "https://www.virustotal.com/api/v3/files";
            if (file.length() > 32 * 1024 * 1024) {
                HttpRequest urlReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.virustotal.com/api/v3/files/upload_url"))
                        .header("x-apikey", VT_API_KEY)
                        .GET()
                        .build();
                HttpResponse<String> urlResp = client.send(urlReq, HttpResponse.BodyHandlers.ofString());
                if (urlResp.statusCode() != 200) {
                    logger.error("Failed to get VirusTotal upload_url: {}", urlResp.body());
                    return false;
                }
                uploadUrl = new JSONObject(urlResp.body()).getString("data");
            }

            // 2. Send file for analysis
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            String CRLF = "\r\n";
            byte[] fileBytes = Files.readAllBytes(file.toPath());

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

            HttpRequest uploadReq = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("x-apikey", VT_API_KEY)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> uploadResp = client.send(uploadReq, HttpResponse.BodyHandlers.ofString());
            if (uploadResp.statusCode() != 200 && uploadResp.statusCode() != 201) {
                logger.error("VirusTotal upload failed (HTTP {}): {}", uploadResp.statusCode(), uploadResp.body());
                return false;
            }
            JSONObject uploadJson = new JSONObject(uploadResp.body());
            String analysisId = uploadJson.getJSONObject("data").getString("id");
            logger.info("VirusTotal analysis ID: {}", analysisId);

            // 3. Poll kết quả phân tích (tối đa 6 lần, mỗi lần cách 5s)
            int maxTries = 6;
            for (int i = 0; i < maxTries; i++) {
                HttpRequest resultReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.virustotal.com/api/v3/analyses/" + analysisId))
                        .header("x-apikey", VT_API_KEY)
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();
                HttpResponse<String> resultResp = client.send(resultReq, HttpResponse.BodyHandlers.ofString());
                if (resultResp.statusCode() != 200) {
                    logger.error("VirusTotal analysis failed (HTTP {}): {}", resultResp.statusCode(), resultResp.body());
                    return false;
                }
                JSONObject resultJson = new JSONObject(resultResp.body());
                JSONObject attr = resultJson.getJSONObject("data").getJSONObject("attributes");
                String status = attr.getString("status");
                if ("completed".equals(status)) {
                    JSONObject stats = attr.getJSONObject("stats");
                    int malicious = stats.optInt("malicious", 0);
                    int suspicious = stats.optInt("suspicious", 0);
                    logger.info("VirusTotal result for {}: malicious={}, suspicious={}", file.getName(), malicious, suspicious);
                    if (malicious > 0 || suspicious > 0) {
                        logger.warn("File {} detected as malicious/suspicious by VirusTotal. Upload blocked.", file.getName());
                        return false;
                    }
                    logger.info("File {} is clean according to VirusTotal.", file.getName());
                    return true;
                } else {
                    logger.info("VirusTotal still analyzing... waiting 5 seconds (try {}/{})", i + 1, maxTries);
                    Thread.sleep(5000);
                }
            }
            logger.error("VirusTotal analysis did not complete in time for file: {}", file.getName());
            return false;
        } catch (Exception e) {
            logger.error("VirusTotal check failed for file {}: {}", file.getName(), e.getMessage(), e);
            return false;
        }
    }
}