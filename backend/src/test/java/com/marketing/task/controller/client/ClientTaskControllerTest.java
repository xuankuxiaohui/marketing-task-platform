package com.marketing.task.controller.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClientTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long testTaskId;

    private HttpHeaders mockUserHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "test-user-001");
        headers.set("X-User-Province", "BJ");
        headers.set("X-User-Role", "vip");
        headers.set("X-User-Tags", "vip,active");
        headers.set("X-User-Org-Id", "org_001");
        headers.set("X-User-Level", "5");
        headers.set("X-Platform", "WEB");
        return headers;
    }

    private HttpHeaders mockAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "admin-test-001");
        return headers;
    }

    @BeforeEach
    void setUp() throws Exception {
        // Create a task with PASSIVE + CLICK steps via admin API
        Map<String, Object> requestBody = Map.of(
                "task", Map.of(
                        "code", "ct_test_" + System.nanoTime(),
                        "name", "Client Integration Test",
                        "description", "Task for client integration testing",
                        "periodType", "ONCE"
                ),
                "steps", List.of(
                        Map.of("code", "ct_passive", "name", "Auto Complete", "type", "PASSIVE", "seq", 1),
                        Map.of("code", "ct_click", "name", "Click to Complete", "type", "CLICK", "seq", 2)
                ),
                "filters", List.of(
                        Map.of("expression", "inProvince(\"BJ\")", "logicOp", "AND",
                                "enabled", true, "seq", 1)
                )
        );

        String saveResponse = mockMvc.perform(post("/api/admin/task")
                        .headers(mockAdminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        testTaskId = objectMapper.readTree(saveResponse).get("data").get("id").asLong();

        // Publish the task
        mockMvc.perform(post("/api/admin/task/{id}/publish", testTaskId)
                        .headers(mockAdminHeaders()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldListAvailableTasks() throws Exception {
        mockMvc.perform(get("/api/client/task/list")
                        .headers(mockUserHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void shouldCreateInstanceOnTaskDetail() throws Exception {
        mockMvc.perform(get("/api/client/task/{taskId}", testTaskId)
                        .headers(mockUserHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.instance").exists())
                .andExpect(jsonPath("$.data.instance.taskId").value(testTaskId))
                .andExpect(jsonPath("$.data.steps").isArray())
                .andExpect(jsonPath("$.data.steps.length()").value(2));
    }

    @Test
    void shouldCompleteClickStepAndFinishInstance() throws Exception {
        // 1. Get task detail (creates instance, PASSIVE auto-completes)
        String detailResponse = mockMvc.perform(get("/api/client/task/{taskId}", testTaskId)
                        .headers(mockUserHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        var detail = objectMapper.readTree(detailResponse);

        // Find the CLICK step
        var steps = detail.get("data").get("steps");
        Long clickStepId = null;
        for (var step : steps) {
            if ("CLICK".equals(step.get("type").asText())) {
                clickStepId = step.get("id").asLong();
                break;
            }
        }

        if (clickStepId == null) {
            throw new AssertionError("No CLICK step found in task detail");
        }

        // 2. Click the CLICK step
        mockMvc.perform(post("/api/client/task/{taskId}/step/{stepId}/click",
                        testTaskId, clickStepId)
                        .headers(mockUserHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void shouldStartTaskExplicitly() throws Exception {
        mockMvc.perform(post("/api/client/task/{taskId}/start", testTaskId)
                        .headers(mockUserHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(testTaskId))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void shouldBeIdempotentOnMultipleDetailCalls() throws Exception {
        // First call creates the instance
        String firstResponse = mockMvc.perform(get("/api/client/task/{taskId}", testTaskId)
                        .headers(mockUserHeaders()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long firstInstanceId = objectMapper.readTree(firstResponse)
                .get("data").get("instance").get("id").asLong();

        // Second call should return the same instance
        String secondResponse = mockMvc.perform(get("/api/client/task/{taskId}", testTaskId)
                        .headers(mockUserHeaders()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long secondInstanceId = objectMapper.readTree(secondResponse)
                .get("data").get("instance").get("id").asLong();

        org.junit.jupiter.api.Assertions.assertEquals(firstInstanceId, secondInstanceId,
                "Multiple detail calls should return the same instance");
    }
}
