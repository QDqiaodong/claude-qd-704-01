package com.fire.protect.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fire.protect.domain.DrillSession;
import com.fire.protect.dto.DrillDtos;
import com.fire.protect.dto.DrillViews;
import com.fire.protect.repository.DrillSessionRepository;
import com.fire.protect.security.CurrentUserHolder;

/**
 * 消防演练模块对外门面：
 * <ol>
 *   <li>查询直接走 {@link DrillViewAssembler}（只读事务）；</li>
 *   <li>动作先做角色校验，再取<strong>场次级互斥锁</strong>（同一场次串行，不同场次并行），
 *       随后进入 {@link DrillTxExecutor} 的单一事务完成「判重 → 校验 → 推进 → 写事件 → 留幂等键」；</li>
 *   <li>后台超时扫描也走同一把锁，与人工动作互斥。</li>
 * </ol>
 */
@Service
public class DrillService {

    /** 场次级互斥锁：同一场次的所有写动作（含超时扫描）串行化。 */
    private final Map<Long, Object> drillLocks = new ConcurrentHashMap<>();

    private final DrillSessionRepository sessionRepository;
    private final DrillViewAssembler assembler;
    private final DrillTxExecutor tx;

    public DrillService(DrillSessionRepository sessionRepository,
                        DrillViewAssembler assembler,
                        DrillTxExecutor tx) {
        this.sessionRepository = sessionRepository;
        this.assembler = assembler;
        this.tx = tx;
    }

    Object lockFor(Long drillId) {
        return drillLocks.computeIfAbsent(drillId, k -> new Object());
    }

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public List<DrillViews.DrillListView> list(String status) {
        List<DrillSession> all = status == null || status.isBlank()
                ? sessionRepository.findAllByOrderByIdDesc()
                : sessionRepository.findByStatusInOrderByIdDesc(List.of(status));
        return all.stream().map(assembler::toListView).toList();
    }

    @Transactional(readOnly = true)
    public DrillViews.DrillDetailView detail(Long id) {
        return assembler.detail(sessionRepository.findById(id)
                .orElseThrow(() -> new com.fire.protect.common.BizException("演练场次不存在：" + id)));
    }

    /** 轻量时钟（执行页高频轮询）：场次状态 + 服务端当前时间 */
    @Transactional(readOnly = true)
    public DrillViews.ClockView clock(Long id) {
        DrillSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new com.fire.protect.common.BizException("演练场次不存在：" + id));
        return new DrillViews.ClockView(s.getId(), s.getStatus(),
                com.fire.protect.domain.DrillDict.sessionStatusName(s.getStatus()),
                com.fire.protect.domain.DrillDict.SESSION_RUNNING.equals(s.getStatus()), LocalDateTime.now());
    }

    // ==================== 创建 / 编排 ====================

    public DrillViews.DrillDetailView create(DrillDtos.CreateDrillReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        return tx.create(req);
    }

    public DrillViews.DrillDetailView saveDraft(Long id, DrillDtos.SaveDraftReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(id)) {
            return tx.saveDraft(id, req);
        }
    }

    public DrillViews.DrillDetailView applyScript(Long id, DrillDtos.ApplyScriptReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(id)) {
            return tx.applyScript(id, req);
        }
    }

    // ==================== 场次状态动作 ====================

    public DrillViews.DrillDetailView ready(Long id, DrillDtos.DrillActionReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(id)) {
            return tx.ready(id, req);
        }
    }

    public DrillViews.DrillDetailView start(Long id, DrillDtos.DrillActionReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(id)) {
            return tx.start(id, req);
        }
    }

    public DrillViews.DrillDetailView pause(Long id, DrillDtos.DrillActionReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(id)) {
            return tx.pause(id, req);
        }
    }

    public DrillViews.DrillDetailView resume(Long id, DrillDtos.DrillActionReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(id)) {
            return tx.resume(id, req);
        }
    }

    public DrillViews.DrillDetailView cancel(Long id, DrillDtos.CancelDrillReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(id)) {
            return tx.cancel(id, req);
        }
    }

    public DrillViews.DrillDetailView finish(Long id, DrillDtos.FinishDrillReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(id)) {
            return tx.finish(id, req);
        }
    }

    // ==================== 指令卡动作 ====================

    public DrillViews.DrillDetailView issueCard(Long drillId, Long cardId, DrillDtos.IssueCardReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(drillId)) {
            return tx.issueCard(drillId, cardId, req);
        }
    }

    /** 岗位确认（可对应岗位绑定用户或现场负责人角色；迟到确认也走这里，不改终态只留痕） */
    public DrillViews.DrillDetailView confirmCard(Long drillId, Long cardId, DrillDtos.ConfirmCardReq req) {
        synchronized (lockFor(drillId)) {
            return tx.confirmCard(drillId, cardId, req);
        }
    }

    public DrillViews.DrillDetailView skipCard(Long drillId, Long cardId, DrillDtos.SkipCardReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(drillId)) {
            return tx.skipCard(drillId, cardId, req);
        }
    }

    public DrillViews.DrillDetailView disposeCard(Long drillId, Long cardId, DrillDtos.DispositionReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        synchronized (lockFor(drillId)) {
            return tx.disposeCard(drillId, cardId, req);
        }
    }

    // ==================== 后台超时扫描 ====================

    /**
     * 定时扫描：把进行中场次里已到截止时刻仍在等待响应的卡翻为 TIMEOUT。
     * 逐场次取锁，与人工动作互斥；幂等键不适用于系统超时（状态守卫保证只翻一次）。
     */
    public int sweepTimeouts() {
        List<DrillSession> running = sessionRepository.findByStatusOrderByIdAsc(
                com.fire.protect.domain.DrillDict.SESSION_RUNNING);
        int changed = 0;
        for (DrillSession s : running) {
            synchronized (lockFor(s.getId())) {
                changed += tx.materializeTimeouts(s.getId());
            }
        }
        return changed;
    }
}
