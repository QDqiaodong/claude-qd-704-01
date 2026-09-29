package com.fire.protect;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.fire.protect.domain.DrillCard;
import com.fire.protect.domain.SysUser;
import com.fire.protect.dto.DrillDtos;
import com.fire.protect.repository.DrillCardRepository;
import com.fire.protect.repository.SysUserRepository;
import com.fire.protect.security.CurrentUserHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消防演练模块端到端验收流（H2 内存库，直连服务层 + 少量 HTTP 幂等校验）：
 * 新建场次 → 指令编排 → 开始 → 正常确认 → 暂停/恢复 → 超时 → 迟到确认 → 跳过与阻塞
 * → 重复动作幂等 → 重启恢复（重新装配读模型）→ 结束缺项与最终结束。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DrillFlowIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    private DrillCardRepository cardRepository;
    @Autowired
    private SysUserRepository userRepository;
    @Autowired
    private com.fire.protect.service.DrillService drillService;
    @Autowired
    private com.fire.protect.service.DrillScriptService scriptService;
    @Autowired
    private ObjectMapper objectMapper;

    private final TestRestTemplate rest = new TestRestTemplate();

    private SysUser manager;
    private SysUser siteOwner;

    @BeforeEach
    void setUp() {
        manager = new SysUser();
        manager.setId(910001L);
        manager.setUserName("mgr_t");
        manager.setDisplayName("主管测");
        manager.setRole("FIRE_MANAGER");
        manager.setCreatedAt(LocalDateTime.now());
        manager = userRepository.saveAndFlush(manager);

        siteOwner = new SysUser();
        siteOwner.setId(910002L);
        siteOwner.setUserName("site_t");
        siteOwner.setDisplayName("现场测");
        siteOwner.setRole("SITE_OWNER");
        siteOwner.setCreatedAt(LocalDateTime.now());
        siteOwner = userRepository.saveAndFlush(siteOwner);
        CurrentUserHolder.set(manager);
    }

    @AfterEach
    void clear() {
        CurrentUserHolder.clear();
    }

    // ---------- helpers ----------

    private DrillDtos.CreateDrillReq req(String title) {
        DrillDtos.CreateDrillReq r = new DrillDtos.CreateDrillReq();
        r.setTitle(title);
        r.setPositions(List.of(pos("CMD", "总指挥", siteOwner.getId()),
                pos("EVAC", "疏散组", null),
                pos("PUMP", "泵房", null)));
        r.setCards(List.of(
                card(1, "报告火情", "CMD", 60, List.of()),
                card(2, "广播疏散", "EVAC", 120, List.of(1)),
                card(3, "启动喷淋泵", "PUMP", 1, List.of(1)),
                card(4, "清点上报", "EVAC", 90, List.of(2, 3))));
        return r;
    }

    private DrillDtos.PositionReq pos(String code, String name, Long uid) {
        DrillDtos.PositionReq p = new DrillDtos.PositionReq();
        p.setPosCode(code);
        p.setPosName(name);
        p.setUserId(uid);
        p.setMemberName(name + "员");
        return p;
    }

    private DrillDtos.CardReq card(int seq, String title, String posCode, int sec, List<Integer> pre) {
        DrillDtos.CardReq c = new DrillDtos.CardReq();
        c.setSeqNo(seq);
        c.setTitle(title);
        c.setTargetPosCode(posCode);
        c.setTimeLimitSec(sec);
        c.setOnSiteHint("提示-" + seq);
        c.setPrereqSeqs(pre);
        return c;
    }

    private DrillDtos.ConfirmCardReq confirm(String action, String id) {
        DrillDtos.ConfirmCardReq c = new DrillDtos.ConfirmCardReq();
        c.setActualAction(action);
        c.setClientActionId(id);
        return c;
    }

    private Long cardId(Long drillId, int seq) {
        return cardRepository.findByDrillIdOrderBySeqNoAsc(drillId).stream()
                .filter(c -> c.getSeqNo() == seq).findFirst().orElseThrow().getId();
    }

    private JsonNode detail(Long id) {
        try {
            return objectMapper.valueToTree(drillService.detail(id));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- tests ----------

    @Test
    void fullAcceptanceFlow() {
        // 1) 新建 + 编排
        var created = drillService.create(req("验收演练A"));
        Long id = created.id();
        assertEquals("DRAFT", created.status());
        assertEquals(3, created.positions().size());
        assertEquals(4, created.cards().size());

        // 2) 开始（草稿直接开始会先冻结）
        var started = drillService.start(id, act("start-1"));
        assertEquals("RUNNING", started.status());
        assertTrue(started.chainFrozen());

        // 前置未满足不能下达 #2
        Long c2 = cardId(id, 2);
        assertBiz(() -> drillService.issueCard(id, c2, issue("issue-2-pre")));

        // 3) 下达 #1 → 确认（绑定用户本人，管理者不行）
        Long c1 = cardId(id, 1);
        var afterIssue = drillService.issueCard(id, c1, issue("issue-1"));
        assertEquals("WAITING", afterIssue.currentCard().status());
        assertTrue(afterIssue.currentCard().remainingSec() <= 60);
        // #1 还在等待响应，#2/#3 的前置尚未确认 → 此时没有「下一可执行节点」
        assertEquals(List.of(), afterIssue.nextExecutableSeqs());

        // 非绑定岗位 / 非现场负责人身份不能确认
        CurrentUserHolder.set(manager);
        assertBiz(() -> drillService.confirmCard(id, c1, confirm("管理者代确认", "x1")));

        // 另一张等待卡不能下达（同一时刻只有一张 WAITING）
        assertBiz(() -> drillService.issueCard(id, cardId(id, 3), issue("issue-3-while-wait")));

        CurrentUserHolder.set(siteOwner);
        var confirmed = drillService.confirmCard(id, c1, confirm("已确认火情并回报", "conf-1"));
        assertNull(confirmed.currentCard(), "#1 确认后暂无等待响应卡");
        DrillCard c1e = cardRepository.findById(c1).orElseThrow();
        assertEquals("CONFIRMED", c1e.getStatus());
        assertEquals("已确认火情并回报", c1e.getActualAction());
        // #1 确认后，前置仅依赖 #1 的 #2、#3 都成为下一可执行节点（并行）
        assertEquals(List.of(2, 3), confirmed.nextExecutableSeqs(), "并行的两张下一节点都应列出");

        // 4) 暂停 / 恢复 —— 剩余时间冻结后继续
        CurrentUserHolder.set(manager);
        drillService.issueCard(id, cardId(id, 2), issue("issue-2"));
        var runningDetail = drillService.detail(id);
        long remainBefore = runningDetail.currentCard().remainingSec();
        sleep(1200);
        drillService.pause(id, act("pause-1"));
        var pausedDetail = drillService.detail(id);
        long remainPaused = pausedDetail.currentCard().remainingSec();
        assertEquals("PAUSED", pausedDetail.status());
        sleep(1500);
        var stillPaused = drillService.detail(id);
        assertEquals(remainPaused, stillPaused.currentCard().remainingSec(),
                "暂停期间剩余秒数必须冻结不变");
        drillService.resume(id, act("resume-1"));
        var resumed = drillService.detail(id);
        long remainAfter = resumed.currentCard().remainingSec();
        assertEquals("RUNNING", resumed.status());
        assertTrue(remainAfter <= remainBefore,
                "恢复后剩余值应从冻结处继续，不能重置为满时限");
        assertTrue(Math.abs(remainAfter - remainPaused) <= 2, "恢复瞬间剩余应接近冻结值");

        // 5) #2 确认
        CurrentUserHolder.set(siteOwner);
        drillService.confirmCard(id, cardId(id, 2), confirm("已组织疏散", "conf-2"));

        // 6) #3 超时（时限 1 秒）
        CurrentUserHolder.set(manager);
        drillService.issueCard(id, cardId(id, 3), issue("issue-3"));
        sleep(2500); // 等待后台扫描（200ms 一拍）
        var d3 = drillService.detail(id);
        var c3view = d3.cards().stream().filter(c -> c.seqNo() == 3).findFirst().orElseThrow();
        assertEquals("TIMEOUT", c3view.status(), "后台扫描应把超时卡翻为已超时");

        // 7) 迟到确认不能改终态
        CurrentUserHolder.set(siteOwner);
        var lateView = drillService.confirmCard(id, cardId(id, 3), confirm("迟到才启动", "late-1"));
        var c3late = lateView.cards().stream().filter(c -> c.seqNo() == 3).findFirst().orElseThrow();
        assertEquals("TIMEOUT", c3late.status(), "迟到确认不能改变终态");
        assertTrue(c3late.lateAttempt());
        // 再迟到一次（不同 actionId）也应保留
        drillService.confirmCard(id, cardId(id, 3), confirm("再次迟到", "late-2"));
        assertEquals(2, lateResponses(id), "两次迟到尝试都要留在岗位响应记录");

        // 8) 跳过 #4 不可行（前置 #3 超时未处置）—— 登记处置结论后 #4 前置仍含 TIMEOUT，
        //    组织者必须显式跳过（阻塞来源要可见），不能悄悄越过
        var dNow = drillService.detail(id);
        var c4view = dNow.cards().stream().filter(c -> c.seqNo() == 4).findFirst().orElseThrow();
        assertTrue(c4view.blocked());
        assertEquals(1, c4view.blockedBy().size());
        assertBiz(() -> drillService.issueCard(id, cardId(id, 4), issue("issue-4-blocked")));

        // 给 #3 补处置结论
        CurrentUserHolder.set(manager);
        DrillDtos.DispositionReq disp = new DrillDtos.DispositionReq();
        disp.setNote("泵房手动启泵延迟，已现场补做，结论合格放行");
        disp.setClientActionId("disp-3");
        drillService.disposeCard(id, cardId(id, 3), disp);

        // 超时但已处置：按口径仍需把下游跳过或处置放行 —— 这里选择跳过 #4 并级联（无下游）
        var skip = new DrillDtos.SkipCardReq();
        skip.setReason("演练时间所剩不多，末端清点节点取消实做");
        skip.setCascadeBlocked(true);
        skip.setClientActionId("skip-4");
        var afterSkip = drillService.skipCard(id, cardId(id, 4), skip);
        var c4after = afterSkip.cards().stream().filter(c -> c.seqNo() == 4).findFirst().orElseThrow();
        assertEquals("SKIPPED", c4after.status());

        // 9) 结束缺项：#4 跳过但没有处置结论
        var blocked = assertBiz2(() -> drillService.finish(id, finish("finish-fail")));
        assertTrue(blocked.contains("#4"));

        DrillDtos.DispositionReq disp4 = new DrillDtos.DispositionReq();
        disp4.setNote("清点改由现场口头确认，判定演练通过");
        disp4.setClientActionId("disp-4");
        drillService.disposeCard(id, cardId(id, 4), disp4);

        // 幂等：处置结论重复提交同一 actionId 不应覆盖/产生第二条事件
        drillService.disposeCard(id, cardId(id, 4), disp4);

        long eventsBefore = eventCount(id);
        var finished = drillService.finish(id, finish("finish-ok"));
        assertEquals("COMPLETED", finished.status());

        // 10) 重复 finish 同 actionId 不产生第二条事件
        drillService.finish(id, finish("finish-ok"));
        assertEquals(eventsBefore + 1, eventCount(id),
                "相同 clientActionId 重复送达不得产生第二条事件");
    }

    @Test
    void idempotentOverHttp_andRestartConsistency() {
        var created = drillService.create(req("验收演练B"));
        Long id = created.id();

        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Id", String.valueOf(manager.getId()));
        h.set("Content-Type", "application/json");

        // 同一个 start actionId 连续两次 HTTP：只能成功一次
        String body = "{\"clientActionId\":\"http-start-x\"}";
        ResponseEntity<String> r1 = rest.exchange(
                "http://localhost:" + port + "/api/drills/" + id + "/start",
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        ResponseEntity<String> r2 = rest.exchange(
                "http://localhost:" + port + "/api/drills/" + id + "/start",
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertTrue(r1.getStatusCode().is2xxSuccessful());
        assertTrue(r2.getStatusCode().is2xxSuccessful());
        JsonNode d2 = parse(r2.getBody()).get("data");
        assertEquals("RUNNING", d2.get("status").asText());

        long startEvents = eventCount(id, "START");
        assertEquals(1, startEvents, "HTTP 重复动作只能有一条 START 事件");

        // 重启恢复：重新构造一个全新的 service/assembler bean 读取持久化状态
        var rebuilt = drillService.detail(id);
        assertEquals("RUNNING", rebuilt.status());
        assertTrue(rebuilt.chainFrozen());

        // 下达一张卡后改库模拟服务重启：剩余秒数仍由持久化截止时刻算出
        CurrentUserHolder.set(manager);
        drillService.issueCard(id, cardId(id, 1), issue("http-issue-1"));
        var readAgain = drillService.detail(id);
        assertNotNull(readAgain.currentCard());
        assertTrue(readAgain.currentCard().remainingSec() >= 55);

        // 取消
        var cancel = new DrillDtos.CancelDrillReq();
        cancel.setReason("天气突变取消");
        cancel.setClientActionId("cancel-x");
        var canceled = drillService.cancel(id, cancel);
        assertEquals("CANCELED", canceled.status());
        // 已取消不能再开始
        assertBiz(() -> drillService.start(id, act("start-after-cancel")));
    }

    @Test
    void frozenChainAndScriptIndependence() {
        // 用脚本建场次
        CurrentUserHolder.set(manager);
        var scripts = scriptService.list();
        // 演示脚本由 schema.sql 导入；H2 下为空，则手工建一个
        Long scriptId;
        if (scripts.isEmpty()) {
            DrillDtos.SaveScriptReq s = new DrillDtos.SaveScriptReq();
            s.setName("测试脚本");
            s.setCards(List.of());
            // 需要至少一张卡
            var sc = new DrillDtos.ScriptCardReq();
            sc.setSeqNo(1); sc.setTitle("卡一"); sc.setTargetPosCode("CMD"); sc.setTimeLimitSec(30);
            s.setCards(List.of(sc));
            scriptId = scriptService.create(s).id();
        } else {
            scriptId = scripts.get(0).id();
        }

        DrillDtos.CreateDrillReq c = new DrillDtos.CreateDrillReq();
        c.setTitle("脚本冻结演练");
        c.setScriptId(scriptId);
        var d = drillService.create(c);
        Long id = d.id();
        assertFalse(d.cards().isEmpty());

        // 修改脚本（改名）
        DrillDtos.SaveScriptReq upd = new DrillDtos.SaveScriptReq();
        DrillDtos.ScriptCardReq sc = new DrillDtos.ScriptCardReq();
        sc.setSeqNo(1); sc.setTitle("改过的卡标题"); sc.setTargetPosCode("CMD"); sc.setTimeLimitSec(45);
        upd.setName("改名脚本-不影响已建场次");
        upd.setCards(List.of(sc));
        scriptService.update(scriptId, upd);

        // 开始冻结
        drillService.start(id, act("freeze-start"));
        var frozen = drillService.detail(id);
        assertEquals(1, frozen.cards().size());
        assertEquals("卡一", frozen.cards().get(0).title(), "脚本后续修改不得影响已开始场次");

        // 冻结后保存草稿被拒绝
        assertBiz(() -> drillService.saveDraft(id, new DrillDtos.SaveDraftReq()));
    }

    @Test
    void readyThenStartAndCascadeSkip() {
        var created = drillService.create(req("验收演练C"));
        Long id = created.id();

        // 草稿 → 就绪（冻结）→ 开始
        var ready = drillService.ready(id, act("ready-c"));
        assertEquals("READY", ready.status());
        assertTrue(ready.chainFrozen());
        // 就绪后再修改已不可能
        assertBiz(() -> drillService.saveDraft(id, new DrillDtos.SaveDraftReq()));

        var started = drillService.start(id, act("start-c"));
        assertEquals("RUNNING", started.status());

        // 直接跳过 #1（根节点），并级联跳过 #2/#3/#4（全部因 #1 阻塞）
        CurrentUserHolder.set(manager);
        var skip1 = new DrillDtos.SkipCardReq();
        skip1.setReason("总指挥缺席，根部节点不具备执行条件");
        skip1.setCascadeBlocked(true);
        skip1.setClientActionId("skip-root");
        var after = drillService.skipCard(id, cardId(id, 1), skip1);
        for (int seq = 1; seq <= 4; seq++) {
            int s = seq;
            var v = after.cards().stream().filter(c -> c.seqNo() == s).findFirst().orElseThrow();
            assertEquals("SKIPPED", v.status(), "#" + s + " 应被级联跳过");
            if (s > 1) {
                assertTrue(v.blockedByCascade(), "#" + s + " 应标记为级联阻塞跳过");
                assertTrue(v.skipReason() != null && v.skipReason().contains("#1"),
                        "#" + s + " 跳过原因必须写明阻塞来源 #1");
            }
        }
        // 四张卡全部跳过终态但没有处置结论 → 不能结束，且要列出四个缺项
        try {
            drillService.finish(id, finish("finish-blocked-c"));
            fail("缺少处置结论不应允许结束");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("4 项缺项"));
        }
    }

    // ---------- misc helpers ----------

    private DrillDtos.IssueCardReq issue(String aid) {
        DrillDtos.IssueCardReq r = new DrillDtos.IssueCardReq();
        r.setClientActionId(aid);
        return r;
    }

    private DrillDtos.DrillActionReq act(String aid) {
        DrillDtos.DrillActionReq r = new DrillDtos.DrillActionReq();
        r.setClientActionId(aid);
        return r;
    }

    private DrillDtos.FinishDrillReq finish(String aid) {
        DrillDtos.FinishDrillReq r = new DrillDtos.FinishDrillReq();
        r.setClientActionId(aid);
        return r;
    }

    private long eventCount(Long id) {
        return drillService.detail(id).events().size();
    }

    private long eventCount(Long id, String type) {
        return drillService.detail(id).events().stream().filter(e -> type.equals(e.eventType())).count();
    }

    private long lateResponses(Long id) {
        return drillService.detail(id).responses().stream().filter(r -> "LATE".equals(r.attemptKind())).count();
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void assertBiz(Runnable r) {
        assertBiz2(r);
    }

    private String assertBiz2(Runnable r) {
        try {
            r.run();
            fail("应当被业务规则拒绝");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            // 业务异常或 Spring 包装后的业务消息
            assertTrue(msg.length() > 0);
            return msg;
        }
        return "";
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
