package com.mkt.task.support;

import com.mkt.kernel.json.JsonUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional webhook for schedule-publish-failure (R12.5). Empty or non-https URL is a no-op. */
public class AlertWebhook {

    private static final Logger log = LoggerFactory.getLogger(AlertWebhook.class);

    private final String url;
    private final HttpClient client;

    public AlertWebhook(String url) {
        this.url = url == null ? "" : url.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public void notifySchedulePublishFailure(long taskId, String code, String reason) {
        URI target = httpsPublicUri(url);
        if (target == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "schedule-publish-failure");
        body.put("taskId", taskId);
        body.put("code", code);
        body.put("reason", reason);
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(body)))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("alert webhook rejected, status={}, taskId={}", response.statusCode(), taskId);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("alert webhook interrupted, taskId={}", taskId);
        } catch (Exception ex) {
            log.warn("alert webhook failed, taskId={}", taskId);
        }
    }

    static URI httpsPublicUri(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("alert webhook ignored, invalid uri");
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            log.warn("alert webhook ignored, host={}", uri.getHost());
            return null;
        }
        if (blockedHost(uri.getHost())) {
            log.warn("alert webhook ignored, host={}", uri.getHost());
            return null;
        }
        return uri;
    }

    private static boolean blockedHost(String host) {
        String lower = host.toLowerCase();
        if ("localhost".equals(lower)
                || lower.endsWith(".localhost")
                || "metadata.google.internal".equals(lower)
                || "::1".equals(lower)
                || "[::1]".equals(lower)) {
            return true;
        }
        return ipv4Blocked(lower);
    }

    private static boolean ipv4Blocked(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] oct = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                oct[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (oct[i] < 0 || oct[i] > 255) {
                return true;
            }
        }
        if (oct[0] == 127 || oct[0] == 10 || oct[0] == 0) {
            return true;
        }
        if (oct[0] == 192 && oct[1] == 168) {
            return true;
        }
        if (oct[0] == 169 && oct[1] == 254) {
            return true;
        }
        return oct[0] == 172 && oct[1] >= 16 && oct[1] <= 31;
    }
}
