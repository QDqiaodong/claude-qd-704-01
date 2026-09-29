package com.fire.protect.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 演练模块 HTTP 层验收：鉴权头、参数校验、重复 actionKey 原始双发、倒计时字段、
 * 模板与场次冻结、多线程并发动作。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DrillControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    private JsonNode body(ResultActions ra) throws Exception {
        // MockMvc 未走 CharacterEncodingFilter 时响应按 ISO-8859-1 解码会乱码，强制 UTF-8
        byte[] bytes = ra.andReturn().getResponse().getContentAsByteArray();
        return om.readTree(bytes).get("data");
    }

    private ResultActions call(String userId, String method, String url, Object req) throws Exception {
        // charset 显式 UTF-8：与生产 axios 一致（MockMvc 默认按 ISO-8859-1 解析裸字符串）
        var rb = method.equals("GET") ? get(url)
                : post(url).contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(req == null ? "{}" : om.writeValueAsString(req));
        if (userId != null) {
            rb.header("X-User-Id", userId);
        }
        return mockMvc.perform(rb);
    }

    private long createStartedDrill() throws Exception {
        JsonNode d = body(call("1", "POST", "/api/drills", Map.of()));
        long sid = d.get("id").asLong();
        Map<String, Object> payload = Map.of(
                "title", "HTTP 演练",
                "posts", List.of(
                        Map.of("postCode", "A", "postName", "巡逻岗"),
                        Map.of("postCode", "B", "postName", "消控岗")),
                "cards", List.of(
                        Map.of("clientId", "c0", "seqNo", 1, "title", "卡1", "content", "内容1",
                                "targetPostCode", "A", "timeLimitSeconds", 2, "prereqClientIds", List.of()),
                        Map.of("clientId", "c1", "seqNo", 2, "title", "卡2", "content", "内容2",
                                "targetPostCode", "B", "timeLimitSeconds", 2, "prereqClientIds", List.of("c0"))));
        call("1", "POST", "/api/drills/" + sid + "/draft", payload).andExpect(status().isOk());
        call("1", "POST", "/api/drills/" + sid + "/ready", Map.of("actionKey", "h-ready")).andExpect(status().isOk());
        call("1", "POST", "/api/drills/" + sid + "/start", Map.of("actionKey", "h-start")).andExpect(status().isOk());
        return sid;
    }

    @Test
    void authValidationAndIdempotencyOverHttp() throws Exception {
        // 无身份头 → 403
        mockMvc.perform(post("/api/drills").contentType(MediaType.APPLICATION_JSON_UTF8).content("{}"))
                .andExpect(status().isForbidden());

        long sid = createStartedDrill();

        // 缺少 actionKey → 400
        mockMvc.perform(post("/api/drills/" + sid + "/pause")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON_UTF8).content("{}"))
                .andExpect(status().isBadRequest());

        JsonNode detail = body(call("1", "GET", "/api/drills/" + sid, null));
        long card1 = detail.get("cards").get(0).get("id").asLong();

        // 前置未满足的卡不能下达
        long card2 = detail.get("cards").get(1).get("id").asLong();
        ResultActions blocked = call("1", "POST", "/api/drills/" + sid + "/cards/" + card2 + "/issue",
                Map.of("actionKey", "h-issue2-first"));
        assertThat(blocked.andReturn().getResponse().getStatus()).isEqualTo(400);

        // 下达卡1
        call("1", "POST", "/api/drills/" + sid + "/cards/" + card1 + "/issue",
                Map.of("actionKey", "h-issue1")).andExpect(status().isOk());
        JsonNode waiting = body(call("1", "GET", "/api/drills/" + sid, null));
        JsonNode c1node = waiting.get("cards").get(0);
        assertThat(c1node.get("status").asText()).isEqualTo("WAITING");
        assertThat(c1node.get("remainingSeconds").asInt()).isBetween(0, 2);
        assertThat(waiting.has("serverTime")).isTrue();
        assertThat(waiting.get("currentSeqs").get(0).asInt()).isEqualTo(1);

        // 同一个 issue 动作标识原始重发：200 回放，事件不增加
        int eventsAfterIssue = body(call("1", "GET", "/api/drills/" + sid, null)).get("events").size();
        call("1", "POST", "/api/drills/" + sid + "/cards/" + card1 + "/issue",
                Map.of("actionKey", "h-issue1")).andExpect(status().isOk());
        int eventsAfterReplay = body(call("1", "GET", "/api/drills/" + sid, null)).get("events").size();
        assertThat(eventsAfterReplay).isEqualTo(eventsAfterIssue);

        // 暂停
        call("1", "POST", "/api/drills/" + sid + "/pause", Map.of("actionKey", "h-pause")).andExpect(status().isOk());
        JsonNode paused = body(call("1", "GET", "/api/drills/" + sid, null));
        assertThat(paused.get("status").asText()).isEqualTo("PAUSED");
        assertThat(paused.get("cards").get(0).get("remainingFrozenSeconds").isNull()).isFalse();
        // 暂停期间确认被拒
        ResultActions confirmWhilePaused = call("1", "POST", "/api/drills/" + sid + "/cards/" + card1 + "/confirm",
                Map.of("actionKey", "h-cf-p", "actualAction", "暂停中"));
        assertThat(confirmWhilePaused.andReturn().getResponse().getStatus()).isEqualTo(400);

        // 恢复 + 确认（实际动作缺失 → 400）
        call("1", "POST", "/api/drills/" + sid + "/resume", Map.of("actionKey", "h-resume")).andExpect(status().isOk());
        ResultActions noAction = call("1", "POST", "/api/drills/" + sid + "/cards/" + card1 + "/confirm",
                Map.of("actionKey", "h-cf-bad", "actualAction", "  "));
        assertThat(noAction.andReturn().getResponse().getStatus()).isEqualTo(400);
        call("1", "POST", "/api/drills/" + sid + "/cards/" + card1 + "/confirm",
                Map.of("actionKey", "h-cf1", "actualAction", "确认动作", "remark", "备注")).andExpect(status().isOk());

        // 确认动作重发：200 且无新事件、无第二条尝试
        JsonNode afterConfirm = body(call("1", "GET", "/api/drills/" + sid, null));
        int ev = afterConfirm.get("events").size();
        call("1", "POST", "/api/drills/" + sid + "/cards/" + card1 + "/confirm",
                Map.of("actionKey", "h-cf1", "actualAction", "确认动作")).andExpect(status().isOk());
        JsonNode afterReplay = body(call("1", "GET", "/api/drills/" + sid, null));
        assertThat(afterReplay.get("events").size()).isEqualTo(ev);
        assertThat(afterReplay.get("cards").get(0).get("attempts").size()).isEqualTo(1);

        // 时间线事件顺序：START/ISSUE/PAUSE/RESUME/CONFIRM 依次存在
        List<String> actions = new java.util.ArrayList<>();
        afterReplay.get("events").forEach(e -> actions.add(e.get("action").asText()));
        assertThat(actions).containsSequence("START", "ISSUE", "PAUSE", "RESUME", "CONFIRM");
    }

    @Test
    void concurrentDifferentActionsOnlyOneWinsAndSameKeyOneEvent() throws Exception {
        long sid = createStartedDrill();
        JsonNode detail = body(call("1", "GET", "/api/drills/" + sid, null));
        long card1 = detail.get("cards").get(0).get("id").asLong();
        call("1", "POST", "/api/drills/" + sid + "/cards/" + card1 + "/issue",
                Map.of("actionKey", "c-issue")).andExpect(status().isOk());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        // 两个不同动作标识并发确认：只有一个 200
        Callable<Integer> c1 = () -> call("1", "POST", "/api/drills/" + sid + "/cards/" + card1 + "/confirm",
                Map.of("actionKey", "c-cf-a", "actualAction", "A")).andReturn().getResponse().getStatus();
        Callable<Integer> c2 = () -> call("1", "POST", "/api/drills/" + sid + "/cards/" + card1 + "/confirm",
                Map.of("actionKey", "c-cf-b", "actualAction", "B")).andReturn().getResponse().getStatus();
        Future<Integer> f1 = pool.submit(c1);
        Future<Integer> f2 = pool.submit(c2);
        int s1 = f1.get();
        int s2 = f2.get();
        pool.shutdown();
        int ok = (s1 == 200 ? 1 : 0) + (s2 == 200 ? 1 : 0);
        assertThat(ok).isEqualTo(1);

        JsonNode after = body(call("1", "GET", "/api/drills/" + sid, null));
        assertThat(after.get("cards").get(0).get("status").asText()).isEqualTo("CONFIRMED");
        long confirmEvents = java.util.stream.StreamSupport.stream(after.get("events").spliterator(), false)
                .filter(e -> "CONFIRM".equals(e.get("action").asText())).count();
        assertThat(confirmEvents).isEqualTo(1);
    }

    @Test
    void templateChangeDoesNotTouchStartedSession() throws Exception {
        // 直接建一个脚本
        Map<String, Object> tpl = Map.of(
                "name", "脚本-" + System.nanoTime(),
                "posts", List.of(Map.of("postCode", "X", "postName", "X岗")),
                "cards", List.of(Map.of("seqNo", 1, "title", "模板卡", "content", "c",
                        "targetPostCode", "X", "timeLimitSeconds", 60)));
        ResultActions tr = call("1", "POST", "/api/drills/templates", tpl);
        assertThat(tr.andReturn().getResponse().getStatus()).isEqualTo(200);
        long templateId = body(tr).get("id").asLong();

        long sid = createStartedDrill();
        JsonNode before = body(call("1", "GET", "/api/drills/" + sid, null));
        int cardsBefore = before.get("cards").size();

        // 改脚本 / 删脚本：场次不动
        call("1", "POST", "/api/drills/templates", Map.of(
                "id", templateId,
                "name", before.get("drillNo").asText() + "-不影响",
                "posts", List.of(Map.of("postCode", "Y", "postName", "Y岗")),
                "cards", List.of(
                        Map.of("seqNo", 1, "title", "改了", "content", "c", "targetPostCode", "Y", "timeLimitSeconds", 90),
                        Map.of("seqNo", 2, "title", "新增卡", "content", "c", "targetPostCode", "Y", "timeLimitSeconds", 90),
                        Map.of("seqNo", 3, "title", "再加", "content", "c", "targetPostCode", "Y", "timeLimitSeconds", 90))))
                .andExpect(status().isOk());
        call("1", "POST", "/api/drills/templates/" + templateId + "/delete", null).andExpect(status().isOk());

        JsonNode after = body(call("1", "GET", "/api/drills/" + sid, null));
        assertThat(after.get("cards").size()).isEqualTo(cardsBefore);
        assertThat(after.get("cards").get(0).get("targetPostName").asText()).isEqualTo("巡逻岗");
    }
}
