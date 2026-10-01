package com.github.shikejing.experimentplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP API 集成测试：覆盖发布、回滚、分流、曝光、统计全链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullLifecycle_publishShuntExposureStats() throws Exception {
        // 1. 保存草稿
        mockMvc.perform(post("/api/experiments/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "experimentId": "exp-api",
                          "name": "API实验",
                          "layerId": null,
                          "layerWeightBps": 0,
                          "salt": "api-salt",
                          "variants": [
                            {"name": "A", "weightBps": 5000},
                            {"name": "B", "weightBps": 5000}
                          ]
                        }
                        """))
                .andExpect(status().isOk());

        // 2. 发布
        mockMvc.perform(post("/api/experiments/exp-api/publish"))
                .andExpect(status().isOk());

        // 3. 分流
        MvcResult shuntResult = mockMvc.perform(get("/api/shunt").param("unitId", "user-1"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> shuntBody = objectMapper.readValue(
                shuntResult.getResponse().getContentAsString(), Map.class);
        assertThat(shuntBody.get("unitId")).isEqualTo("user-1");

        // 4. 曝光上报（重复提交两次）
        String exposure = """
                {
                  "eventId": "evt-1",
                  "unitId": "user-1",
                  "experimentId": "exp-api",
                  "variantName": "A",
                  "metric": "click",
                  "value": 1.0
                }
                """;
        mockMvc.perform(post("/api/exposures").contentType(MediaType.APPLICATION_JSON)
                .content(exposure)).andExpect(status().isOk());
        MvcResult second = mockMvc.perform(post("/api/exposures").contentType(MediaType.APPLICATION_JSON)
                .content(exposure)).andExpect(status().isOk()).andReturn();
        Map<?, ?> secondBody = objectMapper.readValue(second.getResponse().getContentAsString(), Map.class);
        assertThat(secondBody.get("duplicate")).isEqualTo(true);

        // 5. 统计
        MvcResult statsResult = mockMvc.perform(get("/api/stats/exp-api").param("metric", "click"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> statsBody = objectMapper.readValue(
                statsResult.getResponse().getContentAsString(), Map.class);
        assertThat(statsBody.get("totalCount")).isEqualTo(1);
    }

    @Test
    void publishFailure_returns400AndOldVersionServes() throws Exception {
        // 已发布实验占据互斥层 80% 流量
        mockMvc.perform(post("/api/experiments/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"experimentId": "exp-bad", "name": "正常", "layerId": "layer-x",
                         "layerWeightBps": 8000, "salt": "s",
                         "variants": [{"name": "A", "weightBps": 10000}]}
                        """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/experiments/exp-bad/publish"))
                .andExpect(status().isOk());

        // 新实验草稿单独看合法（层权重 5000），但与同层实验合计 13000 > 10000，发布被拒
        mockMvc.perform(post("/api/experiments/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"experimentId": "exp-bad2", "name": "非法", "layerId": "layer-x",
                         "layerWeightBps": 5000, "salt": "s2",
                         "variants": [{"name": "A", "weightBps": 10000}]}
                        """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/experiments/exp-bad2/publish"))
                .andExpect(status().isBadRequest());

        // 旧版本继续服务
        MvcResult result = mockMvc.perform(get("/api/experiments/exp-bad"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("version")).isEqualTo(1);
    }

    @Test
    void rollback_apiReproducesV1Assignments() throws Exception {
        // v1: A 50/50 B
        mockMvc.perform(post("/api/experiments/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"experimentId": "exp-rb", "name": "v1", "layerId": null,
                         "layerWeightBps": 0, "salt": "rb-salt",
                         "variants": [{"name": "A", "weightBps": 5000},
                                      {"name": "B", "weightBps": 5000}]}
                        """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/experiments/exp-rb/publish")).andExpect(status().isOk());

        String v1Variant = shuntVariant("exp-rb", "rb-user-1");

        // v2: A 90/10 B
        mockMvc.perform(post("/api/experiments/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"experimentId": "exp-rb", "name": "v2", "layerId": null,
                         "layerWeightBps": 0, "salt": "rb-salt",
                         "variants": [{"name": "A", "weightBps": 9000},
                                      {"name": "B", "weightBps": 1000}]}
                        """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/experiments/exp-rb/publish")).andExpect(status().isOk());

        // 回滚到 v1
        mockMvc.perform(post("/api/experiments/exp-rb/rollback").param("version", "1"))
                .andExpect(status().isOk());

        // 分配与 v1 一致
        assertThat(shuntVariant("exp-rb", "rb-user-1")).isEqualTo(v1Variant);
    }

    private String shuntVariant(String experimentId, String unitId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/shunt").param("unitId", unitId))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        java.util.List<Map<?, ?>> assignments = (java.util.List<Map<?, ?>>) body.get("assignments");
        return assignments.stream()
                .filter(a -> experimentId.equals(a.get("experimentId")))
                .map(a -> (String) a.get("variant"))
                .findFirst()
                .orElse(null);
    }
}
