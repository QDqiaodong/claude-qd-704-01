package com.fire.protect.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fire.protect.common.BizException;
import com.fire.protect.common.ConflictException;
import com.fire.protect.common.ForbiddenException;
import com.fire.protect.domain.DrillActionIdem;
import com.fire.protect.domain.DrillCard;
import com.fire.protect.domain.DrillCardPrereq;
import com.fire.protect.domain.DrillDict;
import com.fire.protect.domain.DrillEvent;
import com.fire.protect.domain.DrillPosition;
import com.fire.protect.domain.DrillResponse;
import com.fire.protect.domain.DrillScript;
import com.fire.protect.domain.DrillScriptCard;
import com.fire.protect.domain.DrillSession;
import com.fire.protect.domain.SysUser;
import com.fire.protect.dto.DrillDtos;
import com.fire.protect.dto.DrillViews;
import com.fire.protect.repository.DrillActionIdemRepository;
import com.fire.protect.repository.DrillCardPrereqRepository;
import com.fire.protect.repository.DrillCardRepository;
import com.fire.protect.repository.DrillEventRepository;
import com.fire.protect.repository.DrillPositionRepository;
import com.fire.protect.repository.DrillResponseRepository;
import com.fire.protect.repository.DrillScriptCardRepository;
import com.fire.protect.repository.DrillScriptRepository;
import com.fire.protect.repository.DrillSessionRepository;
import com.fire.protect.security.CurrentUserHolder;

/**
 * 演练状态机事务执行器：每个公开方法一个事务，事务内按
 * 「惰性超时判定 → 幂等判重 → 业务校验 → 推进状态 → 写只追加事件 → 留幂等键」执行。
 *
 * <p>调用方（{@link DrillService}）已持有场次级互斥锁，因此同场次动作在此天然串行，
 * check-then-insert 的幂等判重无并发缝隙；{@code (drill_id, client_action_id)}
 * 唯一约束作为跨进程 / 兜底防线。
 */
@Component
public class DrillTxExecutor {

    private final DrillSessionRepository sessionRepository;
    private final DrillPositionRepository positionRepository;
    private final DrillCardRepository cardRepository;
    private final DrillCardPrereqRepository prereqRepository;
    private final DrillEventRepository eventRepository;
    private final DrillResponseRepository responseRepository;
    private final DrillScriptRepository scriptRepository;
    private final DrillScriptCardRepository scriptCardRepository;
    private final DrillActionIdemRepository idemRepository;
    private final DrillViewAssembler assembler;

    public DrillTxExecutor(DrillSessionRepository sessionRepository,
                           DrillPositionRepository positionRepository,
                           DrillCardRepository cardRepository,
                           DrillCardPrereqRepository prereqRepository,
                           DrillEventRepository eventRepository,
                           DrillResponseRepository responseRepository,
                           DrillScriptRepository scriptRepository,
                           DrillScriptCardRepository scriptCardRepository,
                           DrillActionIdemRepository idemRepository,
                           DrillViewAssembler assembler) {
        this.sessionRepository = sessionRepository;
        this.positionRepository = positionRepository;
        this.cardRepository = cardRepository;
        this.prereqRepository = prereqRepository;
        this.eventRepository = eventRepository;
        this.responseRepository = responseRepository;
        this.scriptRepository = scriptRepository;
        this.scriptCardRepository = scriptCardRepository;
        this.idemRepository = idemRepository;
        this.assembler = assembler;
    }

    // ========================================================================
    // 创建 / 草稿编排
    // ========================================================================

    @Transactional
    public DrillViews.DrillDetailView create(DrillDtos.CreateDrillReq req) {
        SysUser organizer = CurrentUserHolder.require();
        if (req == null || req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BizException("演练名称不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        DrillSession session = new DrillSession();
        session.setDrillNo(nextDrillNo());
        session.setTitle(req.getTitle().trim());
        session.setBuildingId(req.getBuildingId());
        session.setScenario(req.getScenario());
        session.setOrganizerName(organizer.getDisplayName());
        session.setStatus(DrillDict.SESSION_DRAFT);
        session.setChainFrozen(false);
        session.setPausedTotalSec(0L);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session = sessionRepository.saveAndFlush(session);

        if (req.getScriptId() != null) {
            applyScriptInto(session.getId(), req.getScriptId());
        } else if (req.getPositions() != null || req.getCards() != null) {
            replaceDraft(session.getId(),
                    req.getPositions() == null ? List.of() : req.getPositions(),
                    req.getCards() == null ? List.of() : req.getCards());
        }
        writeEvent(session.getId(), "CREATE", null, null, null, DrillDict.SESSION_DRAFT,
                "创建演练场次草稿：" + session.getTitle());
        return assembler.detail(ensureSession(session.getId()));
    }

    @Transactional
    public DrillViews.DrillDetailView saveDraft(Long id, DrillDtos.SaveDraftReq req) {
        DrillSession session = requireDraftEditable(id);
        String actionId = req == null ? null : req.getClientActionId();
        materializeTimeouts(id);
        if (firstTime(id, actionId, "SAVE_DRAFT")) {
            replaceDraft(id,
                    req == null || req.getPositions() == null ? List.of() : req.getPositions(),
                    req == null || req.getCards() == null ? List.of() : req.getCards());
            touch(session);
            saveSession(session);
            writeEvent(id, "SAVE_DRAFT", null, null, DrillDict.SESSION_DRAFT, DrillDict.SESSION_DRAFT,
                    "保存编排（岗位 / 指令卡 / 前置节点）");
        }
        return assembler.detail(ensureSession(id));
    }

    @Transactional
    public DrillViews.DrillDetailView applyScript(Long id, DrillDtos.ApplyScriptReq req) {
        DrillSession session = requireDraftEditable(id);
        Long scriptId = req == null ? null : req.getScriptId();
        if (scriptId == null) {
            throw new BizException("请选择要套用的脚本");
        }
        materializeTimeouts(id);
        if (firstTime(id, req.getClientActionId(), "APPLY_SCRIPT")) {
            applyScriptInto(id, scriptId);
            touch(session);
            saveSession(session);
            DrillScript script = scriptRepository.findById(scriptId).orElse(null);
            writeEvent(id, "SAVE_DRAFT", null, null, DrillDict.SESSION_DRAFT, DrillDict.SESSION_DRAFT,
                    "套用常用脚本「" + (script == null ? scriptId : script.getName())
                            + "」，复制为场次独立指令链，此后脚本改动不影响本场");
        }
        return assembler.detail(ensureSession(id));
    }

    // ========================================================================
    // 场次状态：就绪 / 开始 / 暂停 / 恢复 / 取消 / 结束
    // ========================================================================

    @Transactional
    public DrillViews.DrillDetailView ready(Long id, DrillDtos.DrillActionReq req) {
        DrillSession session = ensureSession(id);
        materializeTimeouts(id);
        if (firstTime(id, actionIdOf(req), "READY")) {
            if (!DrillDict.SESSION_DRAFT.equals(session.getStatus())) {
                throw new BizException("只有草稿场次可以置为就绪，当前状态："
                        + DrillDict.sessionStatusName(session.getStatus()));
            }
            validateChain(id);
            LocalDateTime now = LocalDateTime.now();
            String from = session.getStatus();
            session.setStatus(DrillDict.SESSION_READY);
            session.setChainFrozen(true);
            session.setReadyAt(now);
            touch(session);
            saveSession(session);
            writeEvent(id, "READY", null, null, from, DrillDict.SESSION_READY,
                    "指令链冻结，场次就绪（此后修改常用脚本不再影响本场）");
        }
        return assembler.detail(ensureSession(id));
    }

    @Transactional
    public DrillViews.DrillDetailView start(Long id, DrillDtos.DrillActionReq req) {
        DrillSession session = ensureSession(id);
        materializeTimeouts(id);
        if (firstTime(id, actionIdOf(req), "START")) {
            String from = session.getStatus();
            if (!DrillDict.SESSION_DRAFT.equals(from) && !DrillDict.SESSION_READY.equals(from)) {
                throw new BizException("只有草稿或就绪场次可以开始，当前状态："
                        + DrillDict.sessionStatusName(from));
            }
            validateChain(id);
            LocalDateTime now = LocalDateTime.now();
            if (DrillDict.SESSION_DRAFT.equals(from)) {
                session.setReadyAt(now);
            }
            session.setStatus(DrillDict.SESSION_RUNNING);
            session.setChainFrozen(true);
            session.setStartedAt(now);
            touch(session);
            saveSession(session);
            writeEvent(id, "START", null, null, from, DrillDict.SESSION_RUNNING,
                    "开始演练，冻结本场指令链（共 " + cardRepository.findByDrillIdOrderBySeqNoAsc(id).size()
                            + " 张指令卡），倒计时以服务端时间为准");
        }
        return assembler.detail(ensureSession(id));
    }

    @Transactional
    public DrillViews.DrillDetailView pause(Long id, DrillDtos.DrillActionReq req) {
        DrillSession session = ensureSession(id);
        materializeTimeouts(id);
        if (firstTime(id, actionIdOf(req), "PAUSE")) {
            if (!DrillDict.SESSION_RUNNING.equals(session.getStatus())) {
                throw new BizException("只有进行中的演练可以暂停，当前状态："
                        + DrillDict.sessionStatusName(session.getStatus()));
            }
            LocalDateTime now = LocalDateTime.now();
            String from = session.getStatus();
            session.setStatus(DrillDict.SESSION_PAUSED);
            session.setPausedAt(now);
            touch(session);
            saveSession(session);
            DrillCard waiting = findWaiting(id);
            String extra = waiting == null ? "（当前无等待响应的卡）"
                    : "（冻结 #" + waiting.getSeqNo() + "「" + waiting.getTitle() + "」的剩余时间）";
            writeEvent(id, "PAUSE", null, null, from, DrillDict.SESSION_PAUSED, "暂停演练" + extra);
        }
        return assembler.detail(ensureSession(id));
    }

    @Transactional
    public DrillViews.DrillDetailView resume(Long id, DrillDtos.DrillActionReq req) {
        DrillSession session = ensureSession(id);
        if (firstTime(id, actionIdOf(req), "RESUME")) {
            if (!DrillDict.SESSION_PAUSED.equals(session.getStatus())) {
                throw new BizException("只有暂停的演练可以恢复，当前状态："
                        + DrillDict.sessionStatusName(session.getStatus()));
            }
            LocalDateTime now = LocalDateTime.now();
            long frozen = session.getPausedAt() == null ? 0L
                    : Math.max(0L, Duration.between(session.getPausedAt(), now).getSeconds());
            DrillCard waiting = findWaiting(id);
            if (waiting != null && waiting.getDeadlineAt() != null) {
                waiting.setDeadlineAt(waiting.getDeadlineAt().plusSeconds(frozen));
                waiting.setUpdatedAt(now);
                cardRepository.saveAndFlush(waiting);
            }
            session.setPausedTotalSec(session.getPausedTotalSec() + frozen);
            session.setPausedAt(null);
            session.setStatus(DrillDict.SESSION_RUNNING);
            touch(session);
            saveSession(session);
            String extra = waiting == null ? "" : "，#" + waiting.getSeqNo() + " 剩余 "
                    + Math.max(0L, Duration.between(now, waiting.getDeadlineAt()).getSeconds()) + " 秒继续";
            writeEvent(id, "RESUME", null, null, DrillDict.SESSION_PAUSED, DrillDict.SESSION_RUNNING,
                    "恢复演练，本次暂停 " + frozen + " 秒" + extra);
        }
        return assembler.detail(ensureSession(id));
    }

    @Transactional
    public DrillViews.DrillDetailView cancel(Long id, DrillDtos.CancelDrillReq req) {
        DrillSession session = ensureSession(id);
        materializeTimeouts(id);
        if (firstTime(id, req == null ? null : req.getClientActionId(), "CANCEL")) {
            String st = session.getStatus();
            if (DrillDict.SESSION_COMPLETED.equals(st) || DrillDict.SESSION_CANCELED.equals(st)) {
                throw new BizException("演练已终结（" + DrillDict.sessionStatusName(st) + "），不能取消");
            }
            String reason = req == null || req.getReason() == null ? "" : req.getReason().trim();
            if (reason.isBlank()) {
                throw new BizException("取消演练必须填写原因");
            }
            LocalDateTime now = LocalDateTime.now();
            session.setStatus(DrillDict.SESSION_CANCELED);
            session.setCanceledAt(now);
            session.setCanceledBy(CurrentUserHolder.require().getDisplayName());
            session.setCancelReason(reason);
            touch(session);
            saveSession(session);
            writeEvent(id, "CANCEL", null, null, st, DrillDict.SESSION_CANCELED, "取消演练：" + reason);
        }
        return assembler.detail(ensureSession(id));
    }

    @Transactional
    public DrillViews.DrillDetailView finish(Long id, DrillDtos.FinishDrillReq req) {
        DrillSession session = ensureSession(id);
        materializeTimeouts(id);
        if (firstTime(id, req == null ? null : req.getClientActionId(), "FINISH")) {
            String st = session.getStatus();
            if (!DrillDict.SESSION_RUNNING.equals(st) && !DrillDict.SESSION_PAUSED.equals(st)) {
                throw new BizException("只有进行中或暂停的演练可以结束，当前状态："
                        + DrillDict.sessionStatusName(st));
            }
            List<DrillCard> cards = cardRepository.findByDrillIdOrderBySeqNoAsc(id);
            List<DrillViews.FinishBlockerView> blockers = DrillViewAssembler.finishBlockers(cards);
            if (!blockers.isEmpty()) {
                String detail = blockers.stream()
                        .map(b -> "#" + b.seqNo() + "「" + b.title() + "」：" + b.reason())
                        .collect(Collectors.joining("；"));
                throw new BizException("暂不能结束演练，仍有 " + blockers.size() + " 项缺项 —— " + detail);
            }
            LocalDateTime now = LocalDateTime.now();
            session.setStatus(DrillDict.SESSION_COMPLETED);
            session.setFinishedAt(now);
            session.setFinishedBy(CurrentUserHolder.require().getDisplayName());
            touch(session);
            saveSession(session);
            String note = req == null || req.getRemark() == null || req.getRemark().isBlank()
                    ? "" : "；" + req.getRemark().trim();
            writeEvent(id, "FINISH", null, null, st, DrillDict.SESSION_COMPLETED,
                    "结束演练，全部 " + cards.size() + " 张指令卡均处于明确终态，超时/跳过记录处置结论齐全" + note);
        }
        return assembler.detail(ensureSession(id));
    }

    // ========================================================================
    // 指令卡：下达 / 确认（含迟到） / 跳过（含级联） / 处置结论
    // ========================================================================

    @Transactional
    public DrillViews.DrillDetailView issueCard(Long drillId, Long cardId, DrillDtos.IssueCardReq req) {
        DrillSession session = ensureSession(drillId);
        materializeTimeouts(drillId);
        if (firstTime(drillId, req == null ? null : req.getClientActionId(), "ISSUE")) {
            DrillCard card = requireCard(drillId, cardId);
            if (!DrillDict.SESSION_RUNNING.equals(session.getStatus())) {
                throw new BizException("只有进行中的演练可以下达指令，当前状态："
                        + DrillDict.sessionStatusName(session.getStatus()));
            }
            if (!DrillDict.CARD_PENDING.equals(card.getStatus())) {
                throw new BizException("指令 #" + card.getSeqNo() + " 当前是「"
                        + DrillDict.cardStatusName(card.getStatus()) + "」，不能再次下达");
            }
            if (findWaiting(drillId) != null) {
                throw new BizException("已有指令在等待响应，请先确认、超时处置或跳过当前指令后再下达下一张");
            }
            // 前置校验：必须全部「已确认」才能下达；未完成的前置直接提示，终态异常的前置标阻塞来源
            List<DrillCard> prereqs = prereqRepository.findByCardId(card.getId()).stream()
                    .map(e -> cardRepository.findById(e.getPrereqCardId()).orElse(null))
                    .filter(Objects::nonNull).toList();
            List<DrillCard> notReady = prereqs.stream()
                    .filter(p -> !DrillDict.CARD_CONFIRMED.equals(p.getStatus())).toList();
            if (!notReady.isEmpty()) {
                List<DrillCard> hard = notReady.stream().filter(DrillViewAssembler::isHardBlocked).toList();
                if (!hard.isEmpty()) {
                    throw new BizException("前置节点存在阻塞，不能下达 #" + card.getSeqNo() + "："
                            + hard.stream().map(b -> "#" + b.getSeqNo() + "「" + b.getTitle() + "」"
                            + DrillDict.cardStatusName(b.getStatus())).collect(Collectors.joining("、"))
                            + "；需先登记处置结论或把本卡一并跳过（不能越过）");
                }
                throw new BizException("前置节点尚未全部确认，不能下达 #" + card.getSeqNo() + "，待完成："
                        + notReady.stream().map(b -> "#" + b.getSeqNo() + "「" + b.getTitle() + "」"
                        + DrillDict.cardStatusName(b.getStatus())).collect(Collectors.joining("、")));
            }
            LocalDateTime now = LocalDateTime.now();
            card.setStatus(DrillDict.CARD_WAITING);
            card.setIssuedAt(now);
            card.setIssuedBy(CurrentUserHolder.require().getDisplayName());
            card.setDeadlineAt(now.plusSeconds(card.getTimeLimitSec()));
            card.setUpdatedAt(now);
            cardRepository.saveAndFlush(card);
            writeEvent(drillId, "ISSUE", "CARD", card.getId(), DrillDict.CARD_PENDING,
                    DrillDict.CARD_WAITING, "下达 #" + card.getSeqNo() + "「" + card.getTitle()
                            + "」→ " + positionName(card.getTargetPositionId())
                            + "，响应时限 " + card.getTimeLimitSec() + " 秒，截止 "
                            + card.getDeadlineAt().toLocalTime()
                            + (card.getOnSiteHint() == null || card.getOnSiteHint().isBlank()
                            ? "" : "；现场提示：" + card.getOnSiteHint()));
        }
        return assembler.detail(ensureSession(drillId));
    }

    @Transactional
    public DrillViews.DrillDetailView confirmCard(Long drillId, Long cardId, DrillDtos.ConfirmCardReq req) {
        DrillSession session = ensureSession(drillId);
        SysUser user = requireResponder(drillId, cardId);
        materializeTimeouts(drillId);
        if (firstTime(drillId, req == null ? null : req.getClientActionId(), "CONFIRM")) {
            // 超时判定可能刚把卡翻成 TIMEOUT，必须重新加载再判断
            DrillCard card = requireCard(drillId, cardId);
            String action = req == null || req.getActualAction() == null ? "" : req.getActualAction().trim();
            String remark = req == null || req.getRemark() == null ? null : req.getRemark().trim();
            if (action.isBlank()) {
                throw new BizException("岗位确认时必须填写实际动作");
            }
            String st = card.getStatus();
            LocalDateTime now = LocalDateTime.now();
            if (DrillDict.CARD_WAITING.equals(st)) {
                if (DrillDict.SESSION_PAUSED.equals(session.getStatus())) {
                    throw new BizException("演练已暂停，倒计时冻结中，请等待恢复后再确认");
                }
                if (card.getDeadlineAt() != null && !now.isBefore(card.getDeadlineAt())) {
                    // 极端并发兜底：扫描与确认同一秒到达且未被 materialize 翻掉，按超时处理
                    card.setStatus(DrillDict.CARD_TIMEOUT);
                    card.setTimedOutAt(now);
                    card.setUpdatedAt(now);
                    cardRepository.saveAndFlush(card);
                    addResponse(drillId, card, "LATE", action, remark, user, now);
                    card.setLateAttempt(true);
                    cardRepository.saveAndFlush(card);
                    writeEvent(drillId, "TIMEOUT", "CARD", card.getId(), DrillDict.CARD_WAITING,
                            DrillDict.CARD_TIMEOUT, "#" + card.getSeqNo() + "「" + card.getTitle()
                                    + "」在确认到达同时达到截止时刻，判定超时；该确认作为迟到尝试保留，终态不变");
                    writeEvent(drillId, "LATE_ATTEMPT", "CARD", card.getId(), DrillDict.CARD_TIMEOUT,
                            DrillDict.CARD_TIMEOUT, positionName(card.getTargetPositionId())
                                    + " 的确认在 #" + card.getSeqNo() + " 截止时刻到达，记为迟到尝试，实际动作："
                                    + action);
                } else {
                    card.setStatus(DrillDict.CARD_CONFIRMED);
                    card.setConfirmedAt(now);
                    card.setConfirmedBy(user.getDisplayName());
                    card.setActualAction(action);
                    card.setConfirmRemark(remark);
                    card.setUpdatedAt(now);
                    cardRepository.saveAndFlush(card);
                    addResponse(drillId, card, "ON_TIME", action, remark, user, now);
                    long used = card.getIssuedAt() == null ? -1
                            : Duration.between(card.getIssuedAt(), now).getSeconds()
                            - pausedSecondsBetween(drillId);
                    writeEvent(drillId, "CONFIRM", "CARD", card.getId(), DrillDict.CARD_WAITING,
                            DrillDict.CARD_CONFIRMED, positionName(card.getTargetPositionId()) + " 确认 #"
                                    + card.getSeqNo() + "「" + card.getTitle() + "」，实际动作：" + action
                                    + (used >= 0 ? "（净用时约 " + Math.max(0L, used) + " 秒）" : "")
                                    + (remark == null || remark.isBlank() ? "" : "；备注：" + remark));
                }
            } else {
                // 终态（已确认/已超时/已跳过）后到达：迟到尝试，只留痕不改终态
                card.setLateAttempt(true);
                card.setUpdatedAt(now);
                cardRepository.saveAndFlush(card);
                addResponse(drillId, card, "LATE", action, remark, user, now);
                writeEvent(drillId, "LATE_ATTEMPT", "CARD", card.getId(), st, st,
                        positionName(card.getTargetPositionId()) + " 的确认在 #" + card.getSeqNo()
                                + "「" + card.getTitle() + "」已「" + DrillDict.cardStatusName(st)
                                + "」后到达，记为迟到尝试，终态不变；其填报实际动作：" + action);
            }
        }
        return assembler.detail(ensureSession(drillId));
    }

    @Transactional
    public DrillViews.DrillDetailView skipCard(Long drillId, Long cardId, DrillDtos.SkipCardReq req) {
        DrillSession session = ensureSession(drillId);
        materializeTimeouts(drillId);
        if (firstTime(drillId, req == null ? null : req.getClientActionId(), "SKIP")) {
            DrillCard card = requireCard(drillId, cardId);
            String st = session.getStatus();
            if (!DrillDict.SESSION_RUNNING.equals(st) && !DrillDict.SESSION_PAUSED.equals(st)) {
                throw new BizException("只有进行中或暂停的演练可以跳过节点，当前状态："
                        + DrillDict.sessionStatusName(st));
            }
            String reason = req == null || req.getReason() == null ? "" : req.getReason().trim();
            if (reason.isBlank()) {
                throw new BizException("跳过节点必须填写原因");
            }
            if (!DrillDict.CARD_PENDING.equals(card.getStatus())
                    && !DrillDict.CARD_WAITING.equals(card.getStatus())) {
                throw new BizException("#" + card.getSeqNo() + " 已处于终态「"
                        + DrillDict.cardStatusName(card.getStatus()) + "」，不能跳过");
            }
            LocalDateTime now = LocalDateTime.now();
            String fromStatus = card.getStatus();
            markSkipped(card, reason, false, now);
            writeEvent(drillId, "SKIP", "CARD", card.getId(), fromStatus, DrillDict.CARD_SKIPPED,
                    "跳过 #" + card.getSeqNo() + "「" + card.getTitle() + "」，原因：" + reason
                            + (DrillDict.CARD_WAITING.equals(fromStatus) ? "（该卡尚在等待响应，一并终止）" : ""));

            // 级联：沿前置边向下游传播 —— 待下达卡的前置里只要出现已跳过节点（含本次级联），
            // 就带着阻塞来源一起跳过，不能悄悄越过。
            if (req.isCascadeBlocked()) {
                int cascaded = cascadeBlocked(drillId, card, reason, now);
                if (cascaded > 0) {
                    writeEvent(drillId, "SKIP", "SESSION", null, session.getStatus(), session.getStatus(),
                            "因 #" + card.getSeqNo() + " 被跳过，已级联跳过 " + cascaded
                                    + " 张依赖它的下游待下达卡；每张卡均注明阻塞来源，不得悄悄越过");
                }
            }
        }
        return assembler.detail(ensureSession(drillId));
    }

    /**
     * 级联跳过：以本次跳过的卡为起点，沿「前置 → 后置」边向下游扩散，
     * 直到没有新的待下达卡因已跳过前置而被阻塞。每张被级联的卡 skipReason 写明阻塞来源。
     */
    private int cascadeBlocked(Long drillId, DrillCard root, String rootReason, LocalDateTime now) {
        Map<Long, DrillCard> all = cardMap(drillId);
        Set<Long> skippedIds = all.values().stream()
                .filter(c -> DrillDict.CARD_SKIPPED.equals(c.getStatus()))
                .map(DrillCard::getId).collect(Collectors.toCollection(HashSet::new));
        Map<Long, List<Long>> reverse = reversePrereqEdges(all.keySet());

        int cascaded = 0;
        List<Long> queue = new ArrayList<>(List.of(root.getId()));
        Set<Long> queued = new HashSet<>(List.of(root.getId()));
        while (!queue.isEmpty()) {
            Long skippedId = queue.remove(0);
            for (Long downId : reverse.getOrDefault(skippedId, List.of())) {
                DrillCard down = all.get(downId);
                if (down == null || queued.contains(downId)) {
                    continue;
                }
                queued.add(downId);
                if (!DrillDict.CARD_PENDING.equals(down.getStatus())) {
                    continue;
                }
                List<DrillCard> blockers = prereqRepository.findByCardId(downId).stream()
                        .map(e -> all.get(e.getPrereqCardId()))
                        .filter(Objects::nonNull)
                        .filter(b -> DrillDict.CARD_SKIPPED.equals(b.getStatus()))
                        .toList();
                if (blockers.isEmpty()) {
                    continue;
                }
                String src = blockers.stream()
                        .map(b -> "#" + b.getSeqNo() + "「" + b.getTitle() + "」")
                        .collect(Collectors.joining("、"));
                String cascadeReason = "前置节点 " + src + " 已跳过，本卡依赖链阻塞，随 #"
                        + root.getSeqNo() + " 级联跳过；最初原因：" + rootReason;
                markSkipped(down, cascadeReason, true, now);
                skippedIds.add(down.getId());
                queue.add(down.getId());
                cascaded++;
                writeEvent(drillId, "SKIP", "CARD", down.getId(), DrillDict.CARD_PENDING,
                        DrillDict.CARD_SKIPPED, "级联跳过 #" + down.getSeqNo() + "「" + down.getTitle()
                                + "」，" + cascadeReason);
            }
        }
        return cascaded;
    }

    @Transactional
    public DrillViews.DrillDetailView disposeCard(Long drillId, Long cardId, DrillDtos.DispositionReq req) {
        DrillSession session = ensureSession(drillId);
        materializeTimeouts(drillId);
        if (firstTime(drillId, req == null ? null : req.getClientActionId(), "DISPOSE")) {
            DrillCard card = requireCard(drillId, cardId);
            String st = card.getStatus();
            if (!DrillDict.CARD_TIMEOUT.equals(st) && !DrillDict.CARD_SKIPPED.equals(st)) {
                throw new BizException("只有已超时或已跳过的记录需要登记处置结论，当前状态："
                        + DrillDict.cardStatusName(st));
            }
            String sessionStatus = session.getStatus();
            if (!DrillDict.SESSION_RUNNING.equals(sessionStatus)
                    && !DrillDict.SESSION_PAUSED.equals(sessionStatus)) {
                throw new BizException("演练当前为「" + DrillDict.sessionStatusName(sessionStatus)
                        + "」，不能再登记处置结论");
            }
            String note = req == null || req.getNote() == null ? "" : req.getNote().trim();
            if (note.isBlank()) {
                throw new BizException("处置结论不能为空");
            }
            LocalDateTime now = LocalDateTime.now();
            card.setDispositionNote(note);
            card.setDispositionAt(now);
            card.setDispositionBy(CurrentUserHolder.require().getDisplayName());
            card.setUpdatedAt(now);
            cardRepository.saveAndFlush(card);
            String kind = DrillDict.CARD_TIMEOUT.equals(st) ? "超时" : "跳过";
            writeEvent(drillId, "DISPOSE", "CARD", card.getId(), st, st,
                    "登记 #" + card.getSeqNo() + "「" + card.getTitle() + "」" + kind
                            + "处置结论：" + note + "（前置未确认的下游卡仍不能下达，须显式跳过并注明来源）");
        }
        return assembler.detail(ensureSession(drillId));
    }

    // ========================================================================
    // 超时：惰性判定（每个动作前） + 后台扫描
    // ========================================================================

    /**
     * 把进行中场次里已过截止时刻仍 WAITING 的卡翻为 TIMEOUT 并写事件。
     * 状态守卫保证同一张卡只会翻一次（后台扫描与人工动作可能同时到达，取锁后串行）。
     * @return 本次新翻为超时的卡数
     */
    @Transactional
    public int materializeTimeouts(Long drillId) {
        DrillSession session = sessionRepository.findById(drillId).orElse(null);
        if (session == null || !DrillDict.SESSION_RUNNING.equals(session.getStatus())) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;
        for (DrillCard card : cardRepository
                .findExpiredWaiting(List.of(drillId), now)) {
            if (!DrillDict.CARD_WAITING.equals(card.getStatus())) {
                continue;
            }
            card.setStatus(DrillDict.CARD_TIMEOUT);
            card.setTimedOutAt(now);
            card.setUpdatedAt(now);
            cardRepository.saveAndFlush(card);
            long over = Math.max(0L, Duration.between(card.getDeadlineAt(), now).getSeconds());
            writeEvent(drillId, "TIMEOUT", "CARD", card.getId(), DrillDict.CARD_WAITING,
                    DrillDict.CARD_TIMEOUT, " #" + card.getSeqNo() + "「" + card.getTitle() + "」响应超时"
                            + "（时限 " + card.getTimeLimitSec() + " 秒，截止 " + card.getDeadlineAt().toLocalTime()
                            + "，已逾 " + over + " 秒）；需补处置结论后下游才可继续");
            changed++;
        }
        return changed;
    }

    // ========================================================================
    // 编排落库（仅草稿）
    // ========================================================================

    /**
     * 草稿整体替换：删除原岗位 / 卡 / 前置，按入参重建。
     * 岗位身份是代号 posCode；卡的目标岗位用代号映射；前置用顺序号映射。
     */
    private void replaceDraft(Long drillId, List<DrillDtos.PositionReq> positionReqs,
                              List<DrillDtos.CardReq> cardReqs) {
        // ---- 先按入参校验，避免校验失败把草稿清空（同事务也会回滚，双保险） ----
        validatePayload(positionReqs, cardReqs);

        // ---- 清空旧编排 ----
        List<DrillCard> oldCards = cardRepository.findByDrillIdOrderBySeqNoAsc(drillId);
        for (DrillCard c : oldCards) {
            prereqRepository.deleteAllRelated(c.getId());
        }
        prereqRepository.flush();
        cardRepository.deleteAll(oldCards);
        cardRepository.flush();
        positionRepository.deleteAll(positionRepository.findByDrillIdOrderBySortNoAscIdAsc(drillId));
        positionRepository.flush();

        // ---- 建岗位（数组顺序即展示顺序） ----
        LocalDateTime now = LocalDateTime.now();
        Map<String, Long> posIdByCode = new LinkedHashMap<>();
        int sort = 0;
        for (DrillDtos.PositionReq p : positionReqs) {
            DrillPosition entity = new DrillPosition();
            entity.setDrillId(drillId);
            entity.setPosCode(p.getPosCode().trim());
            entity.setPosName(p.getPosName().trim());
            entity.setMemberName(blankToNull(p.getMemberName()));
            entity.setUserId(p.getUserId());
            entity.setContact(blankToNull(p.getContact()));
            entity.setSortNo(++sort);
            entity.setCreatedAt(now);
            posIdByCode.put(entity.getPosCode(), positionRepository.saveAndFlush(entity).getId());
        }

        // ---- 建卡（按顺序号） ----
        List<DrillDtos.CardReq> ordered = cardReqs.stream()
                .sorted((a, b) -> Integer.compare(a.getSeqNo(), b.getSeqNo())).toList();
        Map<Integer, Long> cardIdBySeq = new LinkedHashMap<>();
        for (DrillDtos.CardReq c : ordered) {
            DrillCard entity = new DrillCard();
            entity.setDrillId(drillId);
            entity.setSeqNo(c.getSeqNo());
            entity.setTitle(c.getTitle().trim());
            String code = c.getTargetPosCode() == null ? null : c.getTargetPosCode().trim();
            Long targetId = c.getTargetPositionId() != null && posIdByCode.containsValue(c.getTargetPositionId())
                    ? c.getTargetPositionId() : posIdByCode.get(code);
            if (targetId == null) {
                throw new BizException("第 " + c.getSeqNo() + " 张卡的目标岗位不存在："
                        + (code == null ? c.getTargetPositionId() : code));
            }
            entity.setTargetPositionId(targetId);
            entity.setTimeLimitSec(c.getTimeLimitSec());
            entity.setOnSiteHint(blankToNull(c.getOnSiteHint()));
            entity.setStatus(DrillDict.CARD_PENDING);
            entity.setLateAttempt(false);
            entity.setBlockedByCascade(false);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            cardIdBySeq.put(c.getSeqNo(), cardRepository.saveAndFlush(entity).getId());
        }

        // ---- 建前置边（按顺序号） ----
        for (DrillDtos.CardReq c : ordered) {
            List<Integer> seqs = c.getPrereqSeqs() == null ? List.of()
                    : c.getPrereqSeqs().stream().distinct().toList();
            for (Integer preSeq : seqs) {
                Long preId = cardIdBySeq.get(preSeq);
                Long selfId = cardIdBySeq.get(c.getSeqNo());
                if (preId == null) {
                    throw new BizException("第 " + c.getSeqNo() + " 张卡引用了不存在的前置顺序号：" + preSeq);
                }
                DrillCardPrereq edge = new DrillCardPrereq();
                edge.setCardId(selfId);
                edge.setPrereqCardId(preId);
                prereqRepository.saveAndFlush(edge);
            }
        }
    }

    private void validatePayload(List<DrillDtos.PositionReq> positionReqs,
                                 List<DrillDtos.CardReq> cardReqs) {
        Set<String> codes = new HashSet<>();
        if (positionReqs != null) {
            for (DrillDtos.PositionReq p : positionReqs) {
                if (p.getPosCode() == null || p.getPosCode().isBlank()) {
                    throw new BizException("岗位代号不能为空");
                }
                if (p.getPosName() == null || p.getPosName().isBlank()) {
                    throw new BizException("岗位「" + p.getPosCode() + "」名称不能为空");
                }
                if (!codes.add(p.getPosCode().trim())) {
                    throw new BizException("岗位代号重复：" + p.getPosCode());
                }
            }
        }
        if (cardReqs == null || cardReqs.isEmpty()) {
            return;
        }
        Set<Integer> seqs = new HashSet<>();
        for (DrillDtos.CardReq c : cardReqs) {
            if (c.getSeqNo() == null || c.getSeqNo() <= 0) {
                throw new BizException("指令卡顺序号必须为正整数");
            }
            if (!seqs.add(c.getSeqNo())) {
                throw new BizException("指令卡顺序号重复：" + c.getSeqNo());
            }
            if (c.getTitle() == null || c.getTitle().isBlank()) {
                throw new BizException("第 " + c.getSeqNo() + " 张卡标题不能为空");
            }
            if (c.getTimeLimitSec() == null || c.getTimeLimitSec() <= 0) {
                throw new BizException("第 " + c.getSeqNo() + " 张卡响应时限必须为正整数秒");
            }
            String code = c.getTargetPosCode() == null ? null : c.getTargetPosCode().trim();
            Long targetId = c.getTargetPositionId();
            boolean targetOk = (code != null && codes.contains(code))
                    || (targetId != null && positionReqs != null && positionReqs.stream()
                    .anyMatch(p -> p.getId() != null && p.getId().equals(targetId)));
            if (!targetOk) {
                throw new BizException("第 " + c.getSeqNo() + " 张卡必须选择已编排的目标岗位");
            }
            if (c.getPrereqSeqs() != null) {
                for (Integer pre : c.getPrereqSeqs()) {
                    if (pre.equals(c.getSeqNo())) {
                        throw new BizException("第 " + c.getSeqNo() + " 张卡不能把自己设为前置节点");
                    }
                    if (!seqs.contains(pre)) {
                        // seqs 是到当前为止已收集的；存在前向引用（前置顺序号更大）也不允许，便于一次校验
                        if (cardReqs.stream().noneMatch(x -> x.getSeqNo().equals(pre))) {
                            throw new BizException("第 " + c.getSeqNo() + " 张卡引用了不存在的前置顺序号：" + pre);
                        }
                    }
                }
            }
        }
        // 全量再校一次前置引用 + 环检测
        Map<Integer, DrillDtos.CardReq> bySeq = new LinkedHashMap<>();
        cardReqs.forEach(c -> bySeq.put(c.getSeqNo(), c));
        for (DrillDtos.CardReq c : cardReqs) {
            if (c.getPrereqSeqs() != null) {
                for (Integer pre : c.getPrereqSeqs()) {
                    if (pre.equals(c.getSeqNo()) || !bySeq.containsKey(pre)) {
                        throw new BizException("第 " + c.getSeqNo() + " 张卡的前置顺序号非法：" + pre);
                    }
                }
            }
        }
        ensureAcyclic(bySeq);
    }

    /** 前置关系有向图环检测（DFS 三色）。 */
    private void ensureAcyclic(Map<Integer, DrillDtos.CardReq> bySeq) {
        Map<Integer, Integer> color = new HashMap<>(); // 0 未访问 / 1 在栈中 / 2 完成
        for (Integer seq : bySeq.keySet()) {
            if (color.getOrDefault(seq, 0) == 0 && hasCycle(seq, bySeq, color)) {
                throw new BizException("前置节点存在循环依赖，请检查指令卡 #" + seq + " 及其前置");
            }
        }
    }

    private boolean hasCycle(Integer seq, Map<Integer, DrillDtos.CardReq> bySeq, Map<Integer, Integer> color) {
        color.put(seq, 1);
        DrillDtos.CardReq c = bySeq.get(seq);
        if (c != null && c.getPrereqSeqs() != null) {
            for (Integer pre : c.getPrereqSeqs()) {
                int pc = color.getOrDefault(pre, 0);
                if (pc == 1) {
                    return true;
                }
                if (pc == 0 && hasCycle(pre, bySeq, color)) {
                    return true;
                }
            }
        }
        color.put(seq, 2);
        return false;
    }

    /** 就绪 / 开始前校验冻结内容：至少一岗位一卡，目标岗位与前置均有效且无环。 */
    private void validateChain(Long drillId) {
        List<DrillPosition> positions = positionRepository.findByDrillIdOrderBySortNoAscIdAsc(drillId);
        List<DrillCard> cards = cardRepository.findByDrillIdOrderBySeqNoAsc(drillId);
        if (positions.isEmpty()) {
            throw new BizException("还没有录入参与岗位，至少需要一个岗位");
        }
        if (cards.isEmpty()) {
            throw new BizException("还没有编排现场指令卡，至少需要一张卡");
        }
        Set<Long> posIds = positions.stream().map(DrillPosition::getId).collect(Collectors.toSet());
        Map<Long, DrillCard> byId = new LinkedHashMap<>();
        cards.forEach(c -> byId.put(c.getId(), c));
        for (DrillCard c : cards) {
            if (!posIds.contains(c.getTargetPositionId())) {
                throw new BizException("#" + c.getSeqNo() + " 的目标岗位已不在本场岗位清单中");
            }
            for (DrillCardPrereq edge : prereqRepository.findByCardId(c.getId())) {
                if (!byId.containsKey(edge.getPrereqCardId())) {
                    throw new BizException("#" + c.getSeqNo() + " 引用了不存在的前置卡");
                }
            }
        }
        // 环检测（卡 id 维度）
        Map<Long, Integer> color = new HashMap<>();
        for (Long id : byId.keySet()) {
            if (color.getOrDefault(id, 0) == 0 && cardHasCycle(id, byId, color)) {
                throw new BizException("前置节点存在循环依赖，请检查指令链");
            }
        }
    }

    private boolean cardHasCycle(Long id, Map<Long, DrillCard> byId, Map<Long, Integer> color) {
        color.put(id, 1);
        for (DrillCardPrereq edge : prereqRepository.findByCardId(id)) {
            Long pre = edge.getPrereqCardId();
            int pc = color.getOrDefault(pre, 0);
            if (pc == 1) {
                return true;
            }
            if (pc == 0 && byId.containsKey(pre) && cardHasCycle(pre, byId, color)) {
                return true;
            }
        }
        color.put(id, 2);
        return false;
    }

    /** 套用脚本：岗位按代号补建（已存在的保留），卡片与前置整体复制为本场副本。 */
    private void applyScriptInto(Long drillId, Long scriptId) {
        DrillScript script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new BizException("演练脚本不存在：" + scriptId));
        List<DrillScriptCard> scriptCards = scriptCardRepository.findByScriptIdOrderBySeqNoAscIdAsc(scriptId);
        if (scriptCards.isEmpty()) {
            throw new BizException("脚本「" + script.getName() + "」没有指令卡，无法套用");
        }
        LocalDateTime now = LocalDateTime.now();

        // 岗位：按代号补齐
        Map<String, DrillPosition> existing = new LinkedHashMap<>();
        for (DrillPosition p : positionRepository.findByDrillIdOrderBySortNoAscIdAsc(drillId)) {
            existing.put(p.getPosCode(), p);
        }
        int sort = existing.size();
        for (DrillScriptCard sc : scriptCards) {
            if (!existing.containsKey(sc.getTargetPosCode())) {
                DrillPosition p = new DrillPosition();
                p.setDrillId(drillId);
                p.setPosCode(sc.getTargetPosCode());
                p.setPosName(sc.getTargetPosCode());
                p.setSortNo(++sort);
                p.setCreatedAt(now);
                p = positionRepository.saveAndFlush(p);
                existing.put(p.getPosCode(), p);
            }
        }

        // 卡片：清掉本场旧卡再复制（脚本即新链）
        List<DrillCard> oldCards = cardRepository.findByDrillIdOrderBySeqNoAsc(drillId);
        for (DrillCard c : oldCards) {
            prereqRepository.deleteAllRelated(c.getId());
        }
        prereqRepository.flush();
        cardRepository.deleteAll(oldCards);
        cardRepository.flush();

        Map<Integer, Long> idBySeq = new LinkedHashMap<>();
        for (DrillScriptCard sc : scriptCards.stream()
                .sorted((a, b) -> Integer.compare(a.getSeqNo(), b.getSeqNo())).toList()) {
            DrillCard c = new DrillCard();
            c.setDrillId(drillId);
            c.setSeqNo(sc.getSeqNo());
            c.setTitle(sc.getTitle());
            c.setTargetPositionId(existing.get(sc.getTargetPosCode()).getId());
            c.setTimeLimitSec(sc.getTimeLimitSec());
            c.setOnSiteHint(sc.getOnSiteHint());
            c.setStatus(DrillDict.CARD_PENDING);
            c.setLateAttempt(false);
            c.setBlockedByCascade(false);
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            idBySeq.put(sc.getSeqNo(), cardRepository.saveAndFlush(c).getId());
        }
        for (DrillScriptCard sc : scriptCards) {
            for (Integer preSeq : DrillScriptService.splitInts(sc.getPrereqSeqs())) {
                DrillCardPrereq edge = new DrillCardPrereq();
                edge.setCardId(idBySeq.get(sc.getSeqNo()));
                edge.setPrereqCardId(idBySeq.get(preSeq));
                prereqRepository.saveAndFlush(edge);
            }
        }
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /** 幂等判重 + 留键。返回 true 表示首次（继续推进）；false 表示重复（直接返回快照）。 */
    private boolean firstTime(Long drillId, String clientActionId, String action) {
        if (clientActionId == null || clientActionId.isBlank()) {
            return true; // 系统调用 / 无键请求不做去重（前端所有动作按钮都带键）
        }
        if (idemRepository.existsByDrillIdAndClientActionId(drillId, clientActionId)) {
            return false;
        }
        DrillActionIdem row = new DrillActionIdem();
        row.setDrillId(drillId);
        row.setClientActionId(clientActionId);
        row.setAction(action);
        row.setCreatedAt(LocalDateTime.now());
        try {
            idemRepository.saveAndFlush(row);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 唯一约束兜底：并发跨进程的另一个请求已先成功
            return false;
        }
        return true;
    }

    private void writeEvent(Long drillId, String type, String targetType, Long targetId,
                            String from, String to, String note) {
        SysUser operator = CurrentUserHolder.get();
        DrillEvent e = new DrillEvent();
        e.setDrillId(drillId);
        e.setEventType(type);
        e.setTargetType(targetType);
        e.setTargetId(targetId);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setOperator(operator == null ? "系统" : operator.getDisplayName());
        e.setOperatorRole(operator == null ? "SYSTEM" : operator.getRole());
        e.setNote(note);
        e.setCreatedAt(LocalDateTime.now());
        eventRepository.save(e);
    }

    /** 给最近一条该卡的事件补说明：事件流只追加，级联信息由各自 SKIP 事件独立表达。 */
    private void addResponse(Long drillId, DrillCard card, String kind, String action, String remark,
                             SysUser user, LocalDateTime now) {
        DrillResponse r = new DrillResponse();
        r.setDrillId(drillId);
        r.setCardId(card.getId());
        r.setPositionId(card.getTargetPositionId());
        r.setAttemptKind(kind);
        r.setActualAction(action);
        r.setRemark(blankToNull(remark));
        r.setResponder(user.getDisplayName());
        r.setCreatedAt(now);
        responseRepository.save(r);
    }

    private void markSkipped(DrillCard card, String reason, boolean cascade, LocalDateTime now) {
        card.setStatus(DrillDict.CARD_SKIPPED);
        card.setSkippedAt(now);
        card.setSkippedBy(CurrentUserHolder.require().getDisplayName());
        card.setSkipReason(reason);
        card.setBlockedByCascade(cascade);
        card.setUpdatedAt(now);
        cardRepository.saveAndFlush(card);
    }

    private Map<Long, DrillCard> cardMap(Long drillId) {
        Map<Long, DrillCard> map = new LinkedHashMap<>();
        cardRepository.findByDrillIdOrderBySeqNoAsc(drillId).forEach(c -> map.put(c.getId(), c));
        return map;
    }

    private Map<Long, List<Long>> reversePrereqEdges(Set<Long> cardIds) {
        Map<Long, List<Long>> reverse = new HashMap<>();
        for (Long id : cardIds) {
            for (DrillCardPrereq edge : prereqRepository.findByCardId(id)) {
                reverse.computeIfAbsent(edge.getPrereqCardId(), k -> new ArrayList<>()).add(id);
            }
        }
        return reverse;
    }

    /** 确认鉴权：岗位绑定了用户就必须本人；未绑定则现场负责人角色可代为确认。 */
    private SysUser requireResponder(Long drillId, Long cardId) {
        SysUser user = CurrentUserHolder.require();
        DrillCard card = requireCard(drillId, cardId);
        DrillPosition pos = positionRepository.findById(card.getTargetPositionId())
                .orElseThrow(() -> new BizException("目标岗位不存在"));
        if (pos.getUserId() != null) {
            if (!pos.getUserId().equals(user.getId())) {
                throw new ForbiddenException("只有该岗位绑定人员（"
                        + (pos.getMemberName() == null ? pos.getPosName() : pos.getMemberName())
                        + "）可以确认，当前身份：" + user.getDisplayName());
            }
        } else if (!"SITE_OWNER".equals(user.getRole())) {
            throw new ForbiddenException("岗位确认需要现场负责人身份，当前身份：" + user.getDisplayName());
        }
        return user;
    }

    private String positionName(Long positionId) {
        return positionRepository.findById(positionId)
                .map(p -> p.getPosName() + "（" + p.getPosCode() + "）").orElse("岗位#" + positionId);
    }

    /** 截至当前累计暂停秒数（用于确认事件展示净用时；暂停中则算到当前时刻）。 */
    private long pausedSecondsBetween(Long drillId) {
        DrillSession s = sessionRepository.findById(drillId).orElse(null);
        if (s == null) {
            return 0L;
        }
        long total = s.getPausedTotalSec();
        if (DrillDict.SESSION_PAUSED.equals(s.getStatus()) && s.getPausedAt() != null) {
            total += Math.max(0L, Duration.between(s.getPausedAt(), LocalDateTime.now()).getSeconds());
        }
        return total;
    }

    private DrillCard findWaiting(Long drillId) {
        return cardRepository.findByDrillIdAndStatus(drillId, DrillDict.CARD_WAITING).stream()
                .findFirst().orElse(null);
    }

    private DrillCard requireCard(Long drillId, Long cardId) {
        DrillCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new BizException("指令卡不存在：" + cardId));
        if (!drillId.equals(card.getDrillId())) {
            throw new BizException("指令卡不属于当前演练场次");
        }
        return card;
    }

    private DrillSession requireDraftEditable(Long id) {
        DrillSession session = ensureSession(id);
        if (!DrillDict.SESSION_DRAFT.equals(session.getStatus())) {
            throw new BizException("指令链已冻结（" + DrillDict.sessionStatusName(session.getStatus())
                    + "），岗位与指令卡不能再修改；请在草稿期完成编排");
        }
        return session;
    }

    private DrillSession ensureSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new BizException("演练场次不存在：" + id));
    }

    private void touch(DrillSession session) {
        session.setUpdatedAt(LocalDateTime.now());
    }

    private void saveSession(DrillSession session) {
        try {
            sessionRepository.saveAndFlush(session);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("演练场次刚被其他页面 / 请求更新，请刷新执行页后重试，本次操作未生效");
        }
    }

    private static String actionIdOf(DrillDtos.DrillActionReq req) {
        return req == null ? null : req.getClientActionId();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String nextDrillNo() {
        String prefix = DrillDict.DRILL_NO_PREFIX + "-" + java.time.LocalDate.now().getYear() + "-";
        long max = 0L;
        for (DrillSession s : sessionRepository.findAll()) {
            String no = s.getDrillNo();
            if (no != null && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Long.parseLong(no.substring(no.length() - 4)));
                } catch (NumberFormatException ignored) {
                    // 老格式不计入
                }
            }
        }
        return prefix + String.format("%04d", max + 1);
    }
}
