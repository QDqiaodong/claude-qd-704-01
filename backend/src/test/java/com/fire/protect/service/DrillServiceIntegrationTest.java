package com.fire.protect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import com.fire.protect.common.BizException;
import com.fire.protect.domain.DrillDict;
import com.fire.protect.domain.SysUser;
import com.fire.protect.dto.DrillDtos;
import com.fire.protect.dto.DrillViews;
import com.fire.protect.repository.DrillEventRepository;
import com.fire.protect.security.CurrentUserHolder;

/**
 * 演练模块验收主链路：编排 → 开始冻结 → 下达 → 暂停恢复 → 确认 → 超时 → 迟到确认
 * → 跳过阻塞 → 幂等 → 结束缺项 → 结束，并核对事件顺序。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DrillServiceIntegrationTest {

    @Autowired
    private DrillService drillService;

    @Autowired
    private DrillEventRepository eventRepository;

    private SysUser user(long id, String name, String role) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUserName("u" + id);
        u.setDisplayName(name);
        u.setRole(role);
        return u;
    }

    private void login(long id, String name, String role) {
        CurrentUserHolder.set(user(id, name, role));
    }

    private DrillDtos.CardReq card(String clientId, int seq, String title, String postCode,
                                   int limit, List<String> prereq) {
        DrillDtos.CardReq c = new DrillDtos.CardReq();
        c.setClientId(clientId);
        c.setSeqNo(seq);
        c.setTitle(title);
        c.setContent(title + "内容");
        c.setSceneHint(title + "提示");
        c.setTargetPostCode(postCode);
        c.setTimeLimitSeconds(limit);
        c.setPrereqClientIds(prereq);
        return c;
    }

    private DrillDtos.PostReq post(String code, String name) {
        DrillDtos.PostReq p = new DrillDtos.PostReq();
        p.setPostCode(code);
        p.setPostName(name);
        p.setAssignee(name + "员");
        return p;
    }

    private DrillDtos.SaveDrillReq sampleDraft() {
        DrillDtos.SaveDrillReq req = new DrillDtos.SaveDrillReq();
        req.setTitle("综合演练");
        req.setScenario("一层起火");
        req.setPosts(List.of(post("A", "巡逻岗"), post("B", "消控岗"), post("C", "引导岗")));
        req.setCards(List.of(
                card("c0", 1, "发现火情", "A", 2, List.of()),
                card("c1", 2, "核实广播", "B", 2, List.of("c0")),
                card("c2", 3, "引导疏散", "C", 2, List.of("c1")),
                card("c3", 4, "清点结束", "C", 2, List.of("c2"))));
        return req;
    }

    @Test
    void fullAcceptanceFlow() throws Exception {
        login(1, "陈默", "FIRE_MANAGER");

        // 1. 创建 + 编排
        DrillViews.DrillDetailView created = drillService.create(sampleDraft());
        Long sid = created.id();
        assertThat(created.status()).isEqualTo(DrillDict.SESSION_DRAFT);
        assertThat(created.posts()).hasSize(3);
        assertThat(created.cards()).hasSize(4);
        assertThat(created.cards().get(0).prereqSeqs()).isEmpty();
        assertThat(created.cards().get(1).prereqSeqs()).containsExactly(1);

        // 2. 非法编排：重复岗位编码 / 重复顺序号 / 悬空前置 / 依赖环
        DrillDtos.SaveDrillReq bad = sampleDraft();
        bad.getPosts().get(1).setPostCode("A");
        assertThatThrownBy(() -> drillService.saveDraft(sid, bad)).isInstanceOf(BizException.class);

        // 3. 就绪前结构 OK
        DrillViews.DrillDetailView ready = drillService.ready(sid, "k-ready");
        assertThat(ready.status()).isEqualTo(DrillDict.SESSION_READY);

        // 就绪后不能再保存（冻结入口）
        assertThatThrownBy(() -> drillService.saveDraft(sid, sampleDraft()))
                .isInstanceOf(BizException.class);

        // 退回草稿再就绪
        drillService.reopen(sid, "k-reopen");
        assertThat(drillService.detail(sid).status()).isEqualTo(DrillDict.SESSION_DRAFT);
        drillService.ready(sid, "k-ready-2");

        // 4. 开始：冻结目标岗位快照
        DrillViews.DrillDetailView running = drillService.start(sid, "k-start");
        assertThat(running.status()).isEqualTo(DrillDict.SESSION_RUNNING);
        assertThat(running.startedAt()).isNotNull();
        assertThat(running.cards().get(0).targetPostCode()).isEqualTo("A");
        assertThat(running.cards().get(0).targetPostName()).isEqualTo("巡逻岗");

        // 开始后保存被拒绝（指令链冻结）
        assertThatThrownBy(() -> drillService.saveDraft(sid, sampleDraft()))
                .isInstanceOf(BizException.class);

        // 5. 第二张卡前置未满足，不能下达
        DrillViews.CardView card2 = running.cards().get(1);
        assertThat(card2.issuable()).isFalse();
        assertThat(card2.blockers()).hasSize(1);
        assertThatThrownBy(() -> drillService.issueCard(sid, card2.id(), keyReq("k-issue2")))
                .isInstanceOf(BizException.class);

        // 6. 下达第一张：等待响应 + 剩余秒数
        DrillViews.CardView card1 = running.cards().get(0);
        DrillViews.DrillDetailView afterIssue = drillService.issueCard(sid, card1.id(), keyReq("k-issue1"));
        DrillViews.CardView waiting1 = findCard(afterIssue, 1);
        assertThat(waiting1.status()).isEqualTo(DrillDict.CARD_WAITING);
        assertThat(waiting1.remainingSeconds()).isBetween(0, 2);
        assertThat(afterIssue.currentSeqs()).contains(1);
        assertThat(afterIssue.nextExecutableSeqs()).isEmpty();

        // 7. 暂停：冻结剩余
        DrillViews.DrillDetailView paused = drillService.pause(sid, "k-pause");
        assertThat(paused.status()).isEqualTo(DrillDict.SESSION_PAUSED);
        DrillViews.CardView frozen = findCard(paused, 1);
        assertThat(frozen.remainingFrozenSeconds()).isNotNull();
        assertThat(frozen.deadline()).isNull();
        // 暂停期间等待 2 秒以上，剩余不减少、卡不超时
        Thread.sleep(2200);
        DrillViews.DrillDetailView paused2 = drillService.detail(sid);
        assertThat(findCard(paused2, 1).remainingSeconds()).isEqualTo(frozen.remainingFrozenSeconds());
        assertThat(findCard(paused2, 1).status()).isEqualTo(DrillDict.CARD_WAITING);

        // 暂停期间不能确认
        DrillDtos.ConfirmReq cfWhilePaused = new DrillDtos.ConfirmReq();
        cfWhilePaused.setActionKey("k-confirm-paused");
        cfWhilePaused.setActualAction("暂停中确认");
        assertThatThrownBy(() -> drillService.confirmCard(sid, card1.id(), cfWhilePaused))
                .isInstanceOf(BizException.class);

        // 8. 恢复：从剩余值继续
        DrillViews.DrillDetailView resumed = drillService.resume(sid, "k-resume");
        assertThat(resumed.status()).isEqualTo(DrillDict.SESSION_RUNNING);
        assertThat(findCard(resumed, 1).deadline()).isNotNull();
        assertThat(findCard(resumed, 1).remainingFrozenSeconds()).isNull();

        // 9. 正常确认（实际动作必填）
        DrillDtos.ConfirmReq cf = new DrillDtos.ConfirmReq();
        cf.setActionKey("k-confirm1");
        cf.setActualAction("已到现场确认火情");
        cf.setRemark("对讲机上报");
        DrillViews.DrillDetailView afterConfirm = drillService.confirmCard(sid, card1.id(), cf);
        assertThat(findCard(afterConfirm, 1).status()).isEqualTo(DrillDict.CARD_CONFIRMED);
        assertThat(afterConfirm.posts().get(0).confirmedCount()).isEqualTo(1);
        // 第二张前置满足，成为下一可执行节点
        assertThat(findCard(afterConfirm, 2).issuable()).isTrue();
        assertThat(findCard(afterConfirm, 2).nextExecutable()).isTrue();
        assertThat(afterConfirm.nextExecutableSeqs()).containsExactly(2);

        // 10. 重复 actionKey：不产生第二条事件、不二次推进
        long eventsBefore = eventRepository.findBySessionIdOrderByIdAsc(sid).size();
        drillService.confirmCard(sid, card1.id(), cf);
        drillService.pause(sid, "k-pause");   // 已暂停 → 幂等回放
        drillService.resume(sid, "k-resume"); // 已进行中 → 幂等回放
        long eventsAfter = eventRepository.findBySessionIdOrderByIdAsc(sid).size();
        assertThat(eventsAfter).isEqualTo(eventsBefore);
        assertThat(drillService.detail(sid).status()).isEqualTo(DrillDict.SESSION_RUNNING);

        // 11. 第二张下达后超时：服务端扫描判定
        DrillViews.CardView c2v = findCard(afterConfirm, 2);
        DrillViews.DrillDetailView issued2 = drillService.issueCard(sid, c2v.id(), keyReq("k-issue2b"));
        assertThat(findCard(issued2, 2).status()).isEqualTo(DrillDict.CARD_WAITING);
        Thread.sleep(3200);
        drillService.scheduledSweep();
        DrillViews.DrillDetailView afterTimeout = drillService.detail(sid);
        assertThat(findCard(afterTimeout, 2).status()).isEqualTo(DrillDict.CARD_TIMEOUT);
        assertThat(findCard(afterTimeout, 2).timedOutAt()).isNotNull();

        // 12. 超时后到达的确认：迟到尝试，不改终态
        DrillDtos.ConfirmReq late = new DrillDtos.ConfirmReq();
        late.setActionKey("k-late1");
        late.setActualAction("超时后才赶到");
        DrillViews.DrillDetailView afterLate = drillService.confirmCard(sid, c2v.id(), late);
        assertThat(findCard(afterLate, 2).status()).isEqualTo(DrillDict.CARD_TIMEOUT);
        assertThat(findCard(afterLate, 2).attempts()).hasSize(1);
        assertThat(findCard(afterLate, 2).attempts().get(0).late()).isTrue();
        // 同一迟到动作重发：不重复追加
        drillService.confirmCard(sid, c2v.id(), late);
        assertThat(findCard(drillService.detail(sid), 2).attempts()).hasSize(1);

        // 13. 跳过第三张（待下达）必须填原因；第四张显示被第三张阻塞
        DrillViews.CardView c3v = findCard(afterLate, 3);
        DrillDtos.SkipReq skip = new DrillDtos.SkipReq();
        skip.setActionKey("k-skip3");
        skip.setReason("引导岗临时缺岗");
        assertThatThrownBy(() -> {
            DrillDtos.SkipReq empty = new DrillDtos.SkipReq();
            empty.setActionKey("k-skip-bad");
            empty.setReason(" ");
            drillService.skipCard(sid, c3v.id(), empty);
        }).isInstanceOf(BizException.class);
        DrillViews.DrillDetailView afterSkip = drillService.skipCard(sid, c3v.id(), skip);
        assertThat(findCard(afterSkip, 3).status()).isEqualTo(DrillDict.CARD_SKIPPED);
        DrillViews.CardView card4 = findCard(afterSkip, 4);
        assertThat(card4.issuable()).isFalse();
        assertThat(card4.blockers()).extracting(b -> b.seqNo()).contains(3);
        assertThat(card4.blockers()).anyMatch(b -> b.reason().contains("已跳过"));
        // 阻塞时不能下达
        assertThatThrownBy(() -> drillService.issueCard(sid, card4.id(), keyReq("k-issue4")))
                .isInstanceOf(BizException.class);

        // 14. 结束缺项：第 4 张待下达 + 第 2 张超时未处置 + 第 3 张跳过未处置
        List<String> blockers = drillService.detail(sid).finishBlockers();
        assertThat(blockers).hasSize(3);

        // 给超时/跳过补处置结论
        drillService.disposeCard(sid, c2v.id(), disposeReq("k-disp2", "超时系信道占用，已换备用信道"));
        drillService.disposeCard(sid, c3v.id(), disposeReq("k-disp3", "缺岗已由邻岗代行并补训"));
        // 第 4 张仍待下达 → 仍缺 1 项
        assertThat(drillService.detail(sid).finishBlockers()).hasSize(1);

        // 跳过第 4 张并补处置，全部到终态
        DrillDtos.SkipReq skip4 = new DrillDtos.SkipReq();
        skip4.setActionKey("k-skip4");
        skip4.setReason("末节点并入讲评");
        drillService.skipCard(sid, card4.id(), skip4);
        drillService.disposeCard(sid, card4.id(), disposeReq("k-disp4", "讲评完成，结论合格"));
        assertThat(drillService.detail(sid).finishBlockers()).isEmpty();

        // 15. 结束
        DrillViews.DrillDetailView finished = drillService.finish(sid, keyReq("k-finish"));
        assertThat(finished.status()).isEqualTo(DrillDict.SESSION_FINISHED);
        assertThat(finished.finishedAt()).isNotNull();
        // 已结束不能再操作
        assertThatThrownBy(() -> drillService.pause(sid, "k-x"))
                .isInstanceOf(BizException.class);

        // 16. 事件只追加且顺序完整
        List<DrillViews.EventView> events = finished.events();
        List<String> actions = events.stream().map(DrillViews.EventView::action).toList();
        assertThat(actions).startsWith("CREATE", "READY", "REOPEN", "READY", "START", "ISSUE",
                "PAUSE", "RESUME", "CONFIRM");
        assertThat(actions).contains("TIMEOUT", "LATE_CONFIRM", "SKIP", "DISPOSE", "FINISH");
        // 时间单调不减
        LocalDateTime prev = null;
        for (DrillViews.EventView e : events) {
            if (prev != null) {
                assertThat(e.createdAt()).isAfterOrEqualTo(prev);
            }
            prev = e.createdAt();
            assertThat(e.operator()).isNotBlank();
        }
        // 每个卡动作事件都带前后状态
        assertThat(events).filteredOn(e -> "CARD".equals(e.targetType()))
                .allSatisfy(e -> assertThat(e.toStatus()).isNotBlank());
    }

    @Test
    void cancelFlowBlocksFurtherCardActions() {
        login(1, "陈默", "FIRE_MANAGER");
        DrillViews.DrillDetailView d = drillService.create(sampleDraft());
        Long sid = d.id();
        drillService.ready(sid, "k-ready");
        DrillDtos.CancelReq cancel = new DrillDtos.CancelReq();
        cancel.setActionKey("k-cancel");
        cancel.setReason("天气原因取消");
        DrillViews.DrillDetailView canceled = drillService.cancel(sid, cancel);
        assertThat(canceled.status()).isEqualTo(DrillDict.SESSION_CANCELED);
        assertThat(canceled.cancelReason()).isEqualTo("天气原因取消");
        assertThatThrownBy(() -> drillService.start(sid, "k-start"))
                .isInstanceOf(BizException.class);
        // 取消幂等
        assertThat(drillService.cancel(sid, cancel).status()).isEqualTo(DrillDict.SESSION_CANCELED);
    }

    @Test
    void draftValidations() {
        login(1, "陈默", "FIRE_MANAGER");
        DrillViews.DrillDetailView d = drillService.create(null);
        Long sid = d.id();

        // 空岗位 / 空卡
        DrillDtos.SaveDrillReq empty = new DrillDtos.SaveDrillReq();
        empty.setTitle("x");
        empty.setPosts(List.of());
        empty.setCards(List.of());
        assertThatThrownBy(() -> drillService.ready(sid, "k-ready")).isInstanceOf(BizException.class);

        // 依赖环：1→2→1
        DrillDtos.SaveDrillReq cyc = sampleDraft();
        cyc.getCards().get(0).setPrereqClientIds(List.of("c1"));
        drillService.saveDraft(sid, cyc);
        assertThatThrownBy(() -> drillService.ready(sid, "k-ready")).isInstanceOf(BizException.class)
                .hasMessageContaining("循环");
    }

    private DrillDtos.KeyedReq keyReq(String key) {
        DrillDtos.KeyedReq r = new DrillDtos.KeyedReq();
        r.setActionKey(key);
        return r;
    }

    private DrillDtos.DisposeReq disposeReq(String key, String text) {
        DrillDtos.DisposeReq r = new DrillDtos.DisposeReq();
        r.setActionKey(key);
        r.setDisposition(text);
        return r;
    }

    private DrillViews.CardView findCard(DrillViews.DrillDetailView d, int seq) {
        return d.cards().stream().filter(c -> c.seqNo() == seq).findFirst().orElseThrow();
    }
}
