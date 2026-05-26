package com.marketing.task.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class AdminTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ADMIN_HEADER = "X-User-Id";
    private static final String ADMIN_USER = "admin-test-001";

    @Test
    void shouldSaveAndPublishTaskAggregate() throws Exception {
        Map<String, Object> requestBody = Map.of(
                "task", Map.of(
                        "code", "it_test_pub",
                        "name", "Integration Publish Test",
                        "description", "Task for publish integration test",
                        "periodType", "ONCE"
                ),
                "steps", List.of(
                        Map.of("code", "passive_start", "name", "Start", "type", "PASSIVE", "seq", 1),
                        Map.of("code", "click_mid", "name", "Click Mid", "type", "CLICK", "seq", 2),
                        Map.of("code", "progress_end", "name", "Progress End", "type", "PROGRESS",
                                "targetValue", 3, "seq", 3)
                ),
                "filters", List.of(
                        Map.of("expression", "inProvince(\"BJ\")", "logicOp", "AND", "enabled", true, "seq", 1)
                )
        );

        // 1. Save aggregate
        String saveResponse = mockMvc.perform(post("/api/admin/task")
                        .header(ADMIN_HEADER, ADMIN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn().getResponse().getContentAsString();

        int taskId = objectMapper.readTree(saveResponse).get("data").get("id").asInt();

        // 2. Publish
        mockMvc.perform(post("/api/admin/task/{id}/publish", taskId)
                        .header(ADMIN_HEADER, ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldListTasksWithFilters() throws Exception {
        // Create and publish a task first
        Map<String, Object> requestBody = Map.of(
                "task", Map.of(
                        "code", "it_test_list",
                        "name", "List Filter Test Task",
                        "description", "Task for list filter test",
                        "periodType", "ONCE"
                ),
                "steps", List.of(
                        Map.of("code", "passive_one", "name", "Step One", "type", "PASSIVE", "seq", 1)
                )
        );

        String saveResponse = mockMvc.perform(post("/api/admin/task")
                        .header(ADMIN_HEADER, ADMIN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int taskId = objectMapper.readTree(saveResponse).get("data").get("id").asInt();

        mockMvc.perform(post("/api/admin/task/{id}/publish", taskId)
                        .header(ADMIN_HEADER, ADMIN_USER))
                .andExpect(status().isOk());

        // List with status filter
        mockMvc.perform(get("/api/admin/task")
                        .param("status", "PUBLISHED")
                        .param("keyword", "List Filter")
                        .param("page", "1")
                        .param("size", "20")
                        .header(ADMIN_HEADER, ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    void shouldGetTaskById() throws Exception {
        Map<String, Object> requestBody = Map.of(
                "task", Map.of(
                        "code", "it_test_get",
                        "name", "Get By Id Test",
                        "periodType", "ONCE"
                ),
                "steps", List.of(
                        Map.of("code", "passive_get", "name", "Get Step", "type", "PASSIVE", "seq", 1)
                )
        );

        String saveResponse = mockMvc.perform(post("/api/admin/task")
                        .header(ADMIN_HEADER, ADMIN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int taskId = objectMapper.readTree(saveResponse).get("data").get("id").asInt();

        mockMvc.perform(get("/api/admin/task/{id}", taskId)
                        .header(ADMIN_HEADER, ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(taskId))
                .andExpect(jsonPath("$.data.code").value("it_test_get"));
    }

    @Test
    void shouldOfflineTask() throws Exception {
        Map<String, Object> requestBody = Map.of(
                "task", Map.of(
                        "code", "it_test_offline",
                        "name", "Offline Test",
                        "periodType", "ONCE"
                ),
                "steps", List.of(
                        Map.of("code", "passive_off", "name", "Off Step", "type", "PASSIVE", "seq", 1)
                )
        );

        String saveResponse = mockMvc.perform(post("/api/admin/task")
                        .header(ADMIN_HEADER, ADMIN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int taskId = objectMapper.readTree(saveResponse).get("data").get("id").asInt();

        mockMvc.perform(post("/api/admin/task/{id}/publish", taskId)
                        .header(ADMIN_HEADER, ADMIN_USER))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/task/{id}/offline", taskId)
                        .header(ADMIN_HEADER, ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
