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

/** Optional webhook for schedule-publish-failure (R12.5). Empty URL is a no-op. */
public class AlertWebhook {

    private static final Logger log = LoggerFactory.getLogger(AlertWebhook.class);

    private final String url;
    private final HttpClient client;

    public AlertWebhook(String url) {
        this.url = url == null ? "" : url.trim();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    public void notifySchedulePublishFailure(long taskId, String code, String reason) {
        if (url.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "schedule-publish-failure");
        body.put("taskId", taskId);
        body.put("code", code);
        body.put("reason", reason);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
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
            log.warn("alert webhook failed, taskId={}", taskId, ex);
        }
    }
}
