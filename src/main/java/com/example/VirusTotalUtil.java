package com.example;

import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cdimascio.dotenv.Dotenv;

public class VirusTotalUtil {

    private static final Logger logger = LoggerFactory.getLogger(VirusTotalUtil.class);
    private static final Dotenv dotenv = Dotenv.load();
    private static final String VT_API_KEY = dotenv.get("VIRUS_TOTAL_API_KEY");

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * @param file File to be checked
     * @return true if file is safe (no malware), false if malicious/suspicious
     */
    public static boolean isFileSafe(File file) {
        if (VT_API_KEY == null || VT_API_KEY.isEmpty()) {
            logger.warn("VirusTotal API key not set. Skipping check for {}", file.getName());
            return true;
        }
        try {
            logger.info("Checking file with VirusTotal: {}", file.getAbsolutePath());

            // 1. Query by SHA256 before uploading (faster)
            String sha256 = getSHA256(file);
            HttpRequest hashReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.virustotal.com/api/v3/files/" + sha256))
                    .header("x-apikey", VT_API_KEY)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> hashResp = client.send(hashReq, HttpResponse.BodyHandlers.ofString());
            if (hashResp.statusCode() == 200) {
                return parseResult(new JSONObject(hashResp.body()), file);
            }

            // 2. Get upload URL if file > 32MB
            String uploadUrl = "https://www.virustotal.com/api/v3/files";
            if (file.length() > 32 * 1024 * 1024) {
                HttpRequest urlReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.virustotal.com/api/v3/files/upload_url"))
                        .header("x-apikey", VT_API_KEY)
                        .GET()
                        .build();
                HttpResponse<String> urlResp = client.send(urlReq, HttpResponse.BodyHandlers.ofString());
                if (urlResp.statusCode() != 200) {
                    logger.error("Failed to get upload_url: {}", urlResp.body());
                    return false;
                }
                uploadUrl = new JSONObject(urlResp.body()).getString("data");
            }

            // 3. Upload file (simpler: ofFile publisher)
            HttpRequest uploadReq = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("x-apikey", VT_API_KEY)
                    .header("Content-Type", "application/octet-stream")
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofFile(file.toPath()))
                    .build();

            HttpResponse<String> uploadResp = client.send(uploadReq, HttpResponse.BodyHandlers.ofString());
            if (uploadResp.statusCode() != 200 && uploadResp.statusCode() != 201) {
                logger.error("Upload failed ({}): {}", uploadResp.statusCode(), uploadResp.body());
                return false;
            }

            JSONObject uploadJson = new JSONObject(uploadResp.body());
            String analysisId = uploadJson.getJSONObject("data").getString("id");
            logger.info("VirusTotal analysis ID: {}", analysisId);

            // 4. Poll result (retry a few times)
            for (int i = 0; i < 5; i++) {
                HttpRequest resultReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.virustotal.com/api/v3/analyses/" + analysisId))
                        .header("x-apikey", VT_API_KEY)
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();

                HttpResponse<String> resultResp = client.send(resultReq, HttpResponse.BodyHandlers.ofString());
                if (resultResp.statusCode() == 200) {
                    JSONObject resultJson = new JSONObject(resultResp.body());
                    String status = resultJson.getJSONObject("data").getJSONObject("attributes").getString("status");
                    if ("completed".equals(status)) {
                        return parseResult(resultJson, file);
                    }
                }
                logger.info("Still analyzing... wait 3s (try {}/5)", i + 1);
                Thread.sleep(3000);
            }

            logger.error("Analysis did not complete in time for {}", file.getName());
            return false;
        } catch (Exception e) {
            logger.error("VirusTotal check failed for {}: {}", file.getName(), e.getMessage(), e);
            return false;
        }
    }

    /** Parse VT JSON result */
    private static boolean parseResult(JSONObject json, File file) {
        JSONObject attr = json.getJSONObject("data").getJSONObject("attributes");
        JSONObject stats = attr.has("last_analysis_stats") ? attr.getJSONObject("last_analysis_stats")
                : attr.getJSONObject("stats");

        int malicious = stats.optInt("malicious", 0);
        int suspicious = stats.optInt("suspicious", 0);
        logger.info("Result for {}: malicious={}, suspicious={}", file.getName(), malicious, suspicious);
        return malicious == 0 && suspicious == 0;
    }

    /** Compute SHA256 of file */
    private static String getSHA256(File file) {
        try (var is = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0)
                digest.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest())
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to compute SHA256 for {}: {}", file.getName(), e.getMessage());
            return "";
        }
    }
}
