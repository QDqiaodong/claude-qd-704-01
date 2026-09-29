package com.fire.protect.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fire.protect.domain.Building;
import com.fire.protect.domain.DrillCard;
import com.fire.protect.domain.DrillCardPrereq;
import com.fire.protect.domain.DrillEvent;
import com.fire.protect.domain.DrillDict;
import com.fire.protect.domain.DrillPosition;
import com.fire.protect.domain.DrillResponse;
import com.fire.protect.domain.DrillSession;
import com.fire.protect.domain.SysUser;
import com.fire.protect.dto.DrillViews;
import com.fire.protect.repository.BuildingRepository;
import com.fire.protect.repository.DrillCardPrereqRepository;
import com.fire.protect.repository.DrillCardRepository;
import com.fire.protect.repository.DrillEventRepository;
import com.fire.protect.repository.DrillPositionRepository;
import com.fire.protect.repository.DrillResponseRepository;
import com.fire.protect.security.CurrentUserHolder;

/**
 * 演练读模型装配：把场次 / 岗位 / 指令卡 / 事件 / 响应拼成执行页需要的一份快照。
 *
 * <p>所有时间量都在这里用<strong>服务端当前时间</strong>现算：
 * <ul>
 *   <li>等待响应卡的 {@code remainingSec}：RUNNING 时 deadline-now（不为负），PAUSED 时冻结值；</li>
 *   <li>当前节点 = 唯一一张 WAITING 卡；</li>
 *   <li>下一可执行 = 前置全确认、自身待下达的卡；</li>
 *   <li>阻塞 = 前置里出现已超时（未处置放行）/ 已跳过（未级联）终态。</li>
 * </ul>
 * 因此刷新页面、服务重启、多执行页交替，拿到的口径都一致。
 */
@Component
public class DrillViewAssembler {

    private final DrillCardRepository cardRepository;
    private final DrillCardPrereqRepository prereqRepository;
    private final DrillPositionRepository positionRepository;
    private final DrillEventRepository eventRepository;
    private final DrillResponseRepository responseRepository;
    private final BuildingRepository buildingRepository;

    public DrillViewAssembler(DrillCardRepository cardRepository,
                              DrillCardPrereqRepository prereqRepository,
                              DrillPositionRepository positionRepository,
                              DrillEventRepository eventRepository,
                              DrillResponseRepository responseRepository,
                              BuildingRepository buildingRepository) {
        this.cardRepository = cardRepository;
        this.prereqRepository = prereqRepository;
        this.positionRepository = positionRepository;
        this.eventRepository = eventRepository;
        this.responseRepository = responseRepository;
        this.buildingRepository = buildingRepository;
    }

    /** 列表行（不展开卡明细） */
    @Transactional(readOnly = true)
    public DrillViews.DrillListView toListView(DrillSession s) {
        List<DrillCard> cards = cardRepository.findByDrillIdOrderBySeqNoAsc(s.getId());
        long confirmed = cards.stream().filter(c -> DrillDict.CARD_CONFIRMED.equals(c.getStatus())).count();
        long timeout = cards.stream().filter(c -> DrillDict.CARD_TIMEOUT.equals(c.getStatus())).count();
        long skipped = cards.stream().filter(c -> DrillDict.CARD_SKIPPED.equals(c.getStatus())).count();
        long waiting = cards.stream().filter(c -> DrillDict.CARD_WAITING.equals(c.getStatus())).count();
        long pending = cards.stream().filter(c -> DrillDict.CARD_PENDING.equals(c.getStatus())).count();
        String buildingName = s.getBuildingId() == null ? ""
                : buildingRepository.findById(s.getBuildingId()).map(Building::getName).orElse("");
        return new DrillViews.DrillListView(s.getId(), s.getDrillNo(), s.getTitle(), s.getBuildingId(),
                buildingName, s.getStatus(), DrillDict.sessionStatusName(s.getStatus()), s.getScenario(),
                s.getOrganizerName(), s.isChainFrozen(), s.getStartedAt(), s.getFinishedAt(),
                cards.size(), confirmed, timeout, skipped, waiting, pending, s.getVersion(), s.getCreatedAt());
    }

    /** 详情 / 执行页完整快照 */
    @Transactional(readOnly = true)
    public DrillViews.DrillDetailView detail(DrillSession session) {
        LocalDateTime now = LocalDateTime.now();
        Long drillId = session.getId();

        List<DrillPosition> positions = positionRepository.findByDrillIdOrderBySortNoAscIdAsc(drillId);
        Map<Long, DrillPosition> posMap = new HashMap<>();
        for (DrillPosition p : positions) {
            posMap.put(p.getId(), p);
        }

        List<DrillCard> cards = cardRepository.findByDrillIdOrderBySeqNoAsc(drillId);
        Map<Long, DrillCard> cardMap = new LinkedHashMap<>();
        for (DrillCard c : cards) {
            cardMap.put(c.getId(), c);
        }
        Map<Long, List<DrillCardPrereq>> prereqEdges = new HashMap<>();
        for (DrillCard c : cards) {
            prereqEdges.put(c.getId(), prereqRepository.findByCardId(c.getId()));
        }

        boolean running = DrillDict.SESSION_RUNNING.equals(session.getStatus());
        boolean paused = DrillDict.SESSION_PAUSED.equals(session.getStatus());
        boolean hasWaiting = cards.stream().anyMatch(c -> DrillDict.CARD_WAITING.equals(c.getStatus()));
        DrillCard currentCard = cards.stream()
                .filter(c -> DrillDict.CARD_WAITING.equals(c.getStatus())).findFirst().orElse(null);
        SysUser currentUser = CurrentUserHolder.get();
        Long currentUserId = currentUser == null ? null : currentUser.getId();

        // 卡视图
        List<DrillViews.CardView> cardViews = new ArrayList<>();
        List<Integer> nextExecutableSeqs = new ArrayList<>();
        for (DrillCard c : cards) {
            List<DrillCardPrereq> edges = prereqEdges.getOrDefault(c.getId(), List.of());
            List<DrillViews.PrereqView> prereqViews = new ArrayList<>();
            List<DrillViews.PrereqView> blockedBy = new ArrayList<>();
            boolean allConfirmed = true;
            for (DrillCardPrereq edge : edges) {
                DrillCard pre = cardMap.get(edge.getPrereqCardId());
                if (pre == null) {
                    continue;
                }
                String st = pre.getStatus();
                boolean satisfied = DrillDict.CARD_CONFIRMED.equals(st);
                boolean blocking = isHardBlocked(pre);
                boolean disposed = (DrillDict.CARD_TIMEOUT.equals(st) || DrillDict.CARD_SKIPPED.equals(st))
                        && pre.getDispositionNote() != null && !pre.getDispositionNote().isBlank();
                DrillViews.PrereqView pv = new DrillViews.PrereqView(pre.getId(), pre.getSeqNo(), pre.getTitle(),
                        st, DrillDict.cardStatusName(st), satisfied, blocking, disposed,
                        pre.getDispositionNote());
                prereqViews.add(pv);
                if (blocking) {
                    blockedBy.add(pv);
                }
                if (!satisfied) {
                    allConfirmed = false;
                }
            }
            boolean noPrereq = edges.isEmpty();
            boolean prereqReady = noPrereq || allConfirmed;

            boolean isPending = DrillDict.CARD_PENDING.equals(c.getStatus());
            // 前置已全部确认（或无前置）的待下达卡 = 下一可执行节点（即使当前另有卡等待响应）
            boolean readyToIssue = isPending && prereqReady;
            // canIssue：真正允许此刻点「下达」——还要求场次进行中且没有别的卡在等待响应
            boolean canIssue = readyToIssue && running && !hasWaiting && blockedBy.isEmpty();
            boolean blocked = isPending && !blockedBy.isEmpty();

            if (readyToIssue && !blocked) {
                nextExecutableSeqs.add(c.getSeqNo());
            }

            Long remainingSec = null;
            if (DrillDict.CARD_WAITING.equals(c.getStatus())) {
                if (running && c.getDeadlineAt() != null) {
                    remainingSec = Math.max(0L, Duration.between(now, c.getDeadlineAt()).getSeconds());
                } else if (paused && c.getDeadlineAt() != null && session.getPausedAt() != null) {
                    // 暂停冻结：恢复前剩余秒数不再变化
                    remainingSec = Math.max(0L, Duration.between(session.getPausedAt(), c.getDeadlineAt()).getSeconds());
                } else if (c.getDeadlineAt() != null) {
                    remainingSec = Math.max(0L, Duration.between(now, c.getDeadlineAt()).getSeconds());
                }
            }

            DrillPosition pos = posMap.get(c.getTargetPositionId());
            cardViews.add(new DrillViews.CardView(
                    c.getId(), c.getSeqNo(), c.getTitle(), c.getTargetPositionId(),
                    pos == null ? "" : pos.getPosCode(),
                    pos == null ? "" : pos.getPosName(),
                    pos == null ? "" : (pos.getMemberName() == null ? "" : pos.getMemberName()),
                    c.getTimeLimitSec(), c.getOnSiteHint(),
                    c.getStatus(), DrillDict.cardStatusName(c.getStatus()),
                    edges.stream().map(DrillCardPrereq::getPrereqCardId).toList(),
                    prereqViews, canIssue, readyToIssue, blocked, blockedBy,
                    remainingSec, c.getIssuedAt(), c.getDeadlineAt(),
                    c.getConfirmedAt(), c.getConfirmedBy(), c.getActualAction(), c.getConfirmRemark(),
                    c.isLateAttempt(), c.getTimedOutAt(), c.getSkippedAt(), c.getSkippedBy(),
                    c.getSkipReason(), c.getDispositionNote(), c.getDispositionAt(), c.getDispositionBy(),
                    c.isBlockedByCascade()));
        }

        DrillViews.CardView currentCardView = currentCard == null ? null
                : cardViews.stream().filter(v -> v.id().equals(currentCard.getId())).findFirst().orElse(null);

        // 岗位视图（独立响应统计）
        List<DrillViews.PositionView> positionViews = new ArrayList<>();
        for (DrillPosition p : positions) {
            List<DrillCard> mine = cards.stream()
                    .filter(c -> c.getTargetPositionId().equals(p.getId())).toList();
            long total = mine.size();
            long waitingN = mine.stream().filter(c -> DrillDict.CARD_WAITING.equals(c.getStatus())).count();
            long pendingN = mine.stream().filter(c -> DrillDict.CARD_PENDING.equals(c.getStatus())).count();
            long confirmedN = mine.stream().filter(c -> DrillDict.CARD_CONFIRMED.equals(c.getStatus())).count();
            long timeoutN = mine.stream().filter(c -> DrillDict.CARD_TIMEOUT.equals(c.getStatus())).count();
            long skippedN = mine.stream().filter(c -> DrillDict.CARD_SKIPPED.equals(c.getStatus())).count();
            DrillCard cur = mine.stream().filter(c -> DrillDict.CARD_WAITING.equals(c.getStatus())).findFirst().orElse(null);
            positionViews.add(new DrillViews.PositionView(p.getId(), p.getPosCode(), p.getPosName(),
                    p.getMemberName(), p.getUserId(), p.getContact(), p.getSortNo(),
                    (int) total, (int) waitingN, (int) pendingN, (int) confirmedN,
                    (int) timeoutN, (int) skippedN,
                    cur == null ? null : cur.getId(),
                    cur == null ? null : cur.getTitle(),
                    p.getUserId() != null && p.getUserId().equals(currentUserId)));
        }

        List<DrillEvent> events = eventRepository.findByDrillIdOrderByIdAsc(drillId);
        List<DrillResponse> responses = responseRepository.findByDrillIdOrderByIdAsc(drillId);
        List<DrillViews.ResponseView> responseViews = new ArrayList<>();
        for (DrillResponse r : responses) {
            DrillCard c = cardMap.get(r.getCardId());
            DrillPosition p = posMap.get(r.getPositionId());
            responseViews.add(new DrillViews.ResponseView(r.getId(), r.getCardId(),
                    c == null ? null : c.getSeqNo(), c == null ? "" : c.getTitle(),
                    r.getPositionId(), p == null ? "" : p.getPosCode(), p == null ? "" : p.getPosName(),
                    r.getAttemptKind(), "ON_TIME".equals(r.getAttemptKind()) ? "按时确认" : "迟到尝试",
                    r.getActualAction(), r.getRemark(), r.getResponder(), r.getCreatedAt()));
        }

        List<DrillViews.EventView> eventViews = new ArrayList<>();
        for (DrillEvent e : events) {
            DrillCard c = e.getTargetId() == null ? null : cardMap.get(e.getTargetId());
            eventViews.add(new DrillViews.EventView(e.getId(), e.getEventType(),
                    DrillDict.eventTypeName(e.getEventType()), e.getTargetType(), e.getTargetId(),
                    c == null ? null : c.getSeqNo(), c == null ? null : c.getTitle(),
                    e.getFromStatus(), DrillDict.cardOrSessionName(e.getTargetType(), e.getFromStatus()),
                    e.getToStatus(), DrillDict.cardOrSessionName(e.getTargetType(), e.getToStatus()),
                    e.getOperator(), e.getOperatorRole(), e.getNote(), e.getCreatedAt()));
        }

        List<DrillViews.TimelineItemView> timeline = buildTimeline(eventViews, responseViews);

        long waitingN = cards.stream().filter(c -> DrillDict.CARD_WAITING.equals(c.getStatus())).count();
        long pendingN = cards.stream().filter(c -> DrillDict.CARD_PENDING.equals(c.getStatus())).count();
        long confirmedN = cards.stream().filter(c -> DrillDict.CARD_CONFIRMED.equals(c.getStatus())).count();
        long timeoutN = cards.stream().filter(c -> DrillDict.CARD_TIMEOUT.equals(c.getStatus())).count();
        long skippedN = cards.stream().filter(c -> DrillDict.CARD_SKIPPED.equals(c.getStatus())).count();

        List<DrillViews.FinishBlockerView> blockers = finishBlockers(cards);

        String buildingName = session.getBuildingId() == null ? ""
                : buildingRepository.findById(session.getBuildingId()).map(Building::getName).orElse("");
        String status = session.getStatus();
        boolean allowEdit = DrillDict.SESSION_DRAFT.equals(status);
        boolean canReady = DrillDict.SESSION_DRAFT.equals(status) && readyable(cards);
        boolean canStart = DrillDict.SESSION_DRAFT.equals(status) || DrillDict.SESSION_READY.equals(status);
        boolean canPause = DrillDict.SESSION_RUNNING.equals(status);
        boolean canResume = DrillDict.SESSION_PAUSED.equals(status);
        boolean canCancel = DrillDict.SESSION_DRAFT.equals(status) || DrillDict.SESSION_READY.equals(status)
                || DrillDict.SESSION_RUNNING.equals(status) || DrillDict.SESSION_PAUSED.equals(status);

        return new DrillViews.DrillDetailView(
                session.getId(), session.getDrillNo(), session.getTitle(), session.getBuildingId(),
                buildingName, session.getScenario(), session.getOrganizerName(),
                status, DrillDict.sessionStatusName(status), session.isChainFrozen(),
                session.getReadyAt(), session.getStartedAt(), session.getPausedAt(), session.getPausedTotalSec(),
                session.getFinishedAt(), session.getFinishedBy(), session.getCanceledAt(),
                session.getCanceledBy(), session.getCancelReason(), now, running, session.getVersion(),
                session.getCreatedAt(), positionViews, cardViews, eventViews, responseViews, timeline,
                currentCard == null ? null : currentCard.getId(), currentCardView,
                nextExecutableSeqs, cards.size(), confirmedN, timeoutN, skippedN, waitingN, pendingN,
                blockers.isEmpty() && (running || paused) && cards.stream()
                        .allMatch(c -> DrillDict.CARD_CONFIRMED.equals(c.getStatus())
                                || DrillDict.CARD_TIMEOUT.equals(c.getStatus())
                                || DrillDict.CARD_SKIPPED.equals(c.getStatus())),
                blockers, allowEdit, canReady, canStart, canPause, canResume, canCancel);
    }

    /**
     * 前置卡是否构成硬阻塞：
     * 已跳过 → 硬阻塞（只能把下游也跳过并写明来源，不能悄悄越过）；
     * 已超时 → 未登记处置结论前硬阻塞；组织者补处置结论视为知悉放行。
     */
    static boolean isHardBlocked(DrillCard prereq) {
        String st = prereq.getStatus();
        if (DrillDict.CARD_SKIPPED.equals(st)) {
            return true;
        }
        if (DrillDict.CARD_TIMEOUT.equals(st)) {
            return prereq.getDispositionNote() == null || prereq.getDispositionNote().isBlank();
        }
        return false;
    }

    /** 结束前缺项：未终态卡 + 超时/跳过但没处置结论的卡 */
    static List<DrillViews.FinishBlockerView> finishBlockers(List<DrillCard> cards) {
        List<DrillViews.FinishBlockerView> list = new ArrayList<>();
        for (DrillCard c : cards) {
            String st = c.getStatus();
            if (DrillDict.CARD_PENDING.equals(st)) {
                list.add(new DrillViews.FinishBlockerView(c.getId(), c.getSeqNo(), c.getTitle(), st,
                        DrillDict.cardStatusName(st), "尚未下达（必须确认、超时处置或跳过并写明原因）"));
            } else if (DrillDict.CARD_WAITING.equals(st)) {
                list.add(new DrillViews.FinishBlockerView(c.getId(), c.getSeqNo(), c.getTitle(), st,
                        DrillDict.cardStatusName(st), "仍在等待岗位响应（需确认或跳过）"));
            } else if ((DrillDict.CARD_TIMEOUT.equals(st) || DrillDict.CARD_SKIPPED.equals(st))
                    && (c.getDispositionNote() == null || c.getDispositionNote().isBlank())) {
                list.add(new DrillViews.FinishBlockerView(c.getId(), c.getSeqNo(), c.getTitle(), st,
                        DrillDict.cardStatusName(st),
                        DrillDict.CARD_TIMEOUT.equals(st) ? "已超时但未填写处置结论" : "已跳过但未填写处置结论"));
            }
        }
        return list;
    }

    private boolean readyable(List<DrillCard> cards) {
        return !cards.isEmpty() && cards.stream().noneMatch(c -> DrillDict.CARD_WAITING.equals(c.getStatus())
                || DrillDict.CARD_CONFIRMED.equals(c.getStatus())
                || DrillDict.CARD_TIMEOUT.equals(c.getStatus())
                || DrillDict.CARD_SKIPPED.equals(c.getStatus()));
    }

    /** 事件 + 岗位响应尝试合并为一条时间线（同秒按先事件后响应，id 次序稳定）。 */
    private List<DrillViews.TimelineItemView> buildTimeline(List<DrillViews.EventView> events,
                                                            List<DrillViews.ResponseView> responses) {
        List<DrillViews.TimelineItemView> items = new ArrayList<>();
        for (DrillViews.EventView e : events) {
            items.add(new DrillViews.TimelineItemView("E-" + e.id(), "EVENT", "事件", e.createdAt(),
                    e.targetSeq(), e.targetTitle(), e.eventType(), e.eventTypeName(),
                    e.fromStatus(), e.fromStatusName(), e.toStatus(), e.toStatusName(),
                    null, null, null, e.operator(), e.operatorRole(), e.note()));
        }
        for (DrillViews.ResponseView r : responses) {
            items.add(new DrillViews.TimelineItemView("R-" + r.id(),
                    "ON_TIME".equals(r.attemptKind()) ? "RESPONSE" : "LATE",
                    "ON_TIME".equals(r.attemptKind()) ? "岗位响应" : "迟到尝试",
                    r.createdAt(), r.cardSeqNo(), r.cardTitle(), null, null, null, null, null, null,
                    r.posCode(), r.posName(), r.attemptKind(), r.responder(), null,
                    r.actualAction() + (r.remark() == null || r.remark().isBlank() ? "" : "（备注：" + r.remark() + "）")));
        }
        items.sort((a, b) -> {
            int cmp = a.occurredAt().compareTo(b.occurredAt());
            if (cmp != 0) {
                return cmp;
            }
            // 同秒：事件先于响应，内部再按 key（id）稳定
            if ("EVENT".equals(a.kind()) && !"EVENT".equals(b.kind())) {
                return -1;
            }
            if (!"EVENT".equals(a.kind()) && "EVENT".equals(b.kind())) {
                return 1;
            }
            return a.key().compareTo(b.key());
        });
        return items;
    }
}
