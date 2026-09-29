package com.fire.protect.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fire.protect.common.BizException;
import com.fire.protect.common.ConflictException;
import com.fire.protect.domain.Building;
import com.fire.protect.domain.DrillCard;
import com.fire.protect.domain.DrillCardDependency;
import com.fire.protect.domain.DrillDict;
import com.fire.protect.domain.DrillEvent;
import com.fire.protect.domain.DrillPost;
import com.fire.protect.domain.DrillResponseAttempt;
import com.fire.protect.domain.DrillSession;
import com.fire.protect.domain.DrillTemplate;
import com.fire.protect.domain.DrillTemplateCard;
import com.fire.protect.domain.DrillTemplatePost;
import com.fire.protect.domain.SysUser;
import com.fire.protect.dto.DrillDtos;
import com.fire.protect.dto.DrillViews;
import com.fire.protect.repository.BuildingRepository;
import com.fire.protect.repository.DrillCardDependencyRepository;
import com.fire.protect.repository.DrillCardRepository;
import com.fire.protect.repository.DrillEventRepository;
import com.fire.protect.repository.DrillPostRepository;
import com.fire.protect.repository.DrillResponseAttemptRepository;
import com.fire.protect.repository.DrillSessionRepository;
import com.fire.protect.repository.DrillTemplateCardRepository;
import com.fire.protect.repository.DrillTemplatePostRepository;
import com.fire.protect.repository.DrillTemplateRepository;
import com.fire.protect.security.CurrentUserHolder;

/**
 * 消防演练现场指令与响应打点。
 *
 * <p>设计口径：
 * <ul>
 *   <li>开始即冻结：START 在同一事务里把目标岗位快照写进每张卡，此后指令链不可改；
 *       常用脚本与场次是两份数据，改脚本不回写场次。</li>
 *   <li>时间在服务端：deadline / 冻结剩余落库，剩余秒数按服务端当前时间计算；
 *       暂停清空 deadline 并冻结剩余，恢复按剩余值重设 deadline，重启不漂移。</li>
 *   <li>事件只追加：每个动作一条事件，action_key 库内同场唯一，重复动作标识直接回放
 *       既有结果，不产生第二条事件、不二次推进。</li>
 *   <li>超时由服务端定时扫描 + 每次动作前兜底扫描产生，不依赖浏览器计时。</li>
 * </ul>
 */
@Service
public class DrillService {

    private final DrillSessionRepository sessionRepository;
    private final DrillPostRepository postRepository;
    private final DrillCardRepository cardRepository;
    private final DrillCardDependencyRepository dependencyRepository;
    private final DrillResponseAttemptRepository attemptRepository;
    private final DrillEventRepository eventRepository;
    private final DrillTemplateRepository templateRepository;
    private final DrillTemplatePostRepository templatePostRepository;
    private final DrillTemplateCardRepository templateCardRepository;
    private final BuildingRepository buildingRepository;

    public DrillService(DrillSessionRepository sessionRepository,
                        DrillPostRepository postRepository,
                        DrillCardRepository cardRepository,
                        DrillCardDependencyRepository dependencyRepository,
                        DrillResponseAttemptRepository attemptRepository,
                        DrillEventRepository eventRepository,
                        DrillTemplateRepository templateRepository,
                        DrillTemplatePostRepository templatePostRepository,
                        DrillTemplateCardRepository templateCardRepository,
                        BuildingRepository buildingRepository) {
        this.sessionRepository = sessionRepository;
        this.postRepository = postRepository;
        this.cardRepository = cardRepository;
        this.dependencyRepository = dependencyRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.templateRepository = templateRepository;
        this.templatePostRepository = templatePostRepository;
        this.templateCardRepository = templateCardRepository;
        this.buildingRepository = buildingRepository;
    }

    // ==================== 列表 / 详情 ====================

    @Transactional(readOnly = true)
    public List<DrillViews.DrillListItem> list(String status) {
        List<DrillSession> sessions = (status == null || status.isBlank())
                ? sessionRepository.findAllByOrderByIdDesc()
                : sessionRepository.findByStatusOrderByIdDesc(status);
        List<DrillViews.DrillListItem> result = new ArrayList<>();
        for (DrillSession s : sessions) {
            result.add(toListItem(s));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public DrillViews.DrillDetailView detail(Long id) {
        return buildDetail(ensureSession(id), LocalDateTime.now());
    }

    // ==================== 草稿：创建 / 保存 ====================

    @Transactional
    public DrillViews.DrillDetailView create(DrillDtos.SaveDrillReq req) {
        SysUser user = CurrentUserHolder.require();
        LocalDateTime now = nowSec();
        DrillSession session = new DrillSession();
        session.setDrillNo(nextDrillNo());
        session.setTitle("新建演练");
        session.setOrganizer(user.getDisplayName());
        session.setStatus(DrillDict.SESSION_DRAFT);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        DrillSession saved = sessionRepository.saveAndFlush(session);
        writeEvent(saved.getId(), "CREATE", "SESSION", saved.getId(), null,
                null, DrillDict.SESSION_DRAFT, "创建演练场次草稿", user.getDisplayName(), now);
        // 空请求体（{ }）= 只建空白草稿，随后进编排页；带内容才应用编排
        if (req != null && (req.getTitle() != null && !req.getTitle().isBlank()
                || (req.getPosts() != null && !req.getPosts().isEmpty())
                || (req.getCards() != null && !req.getCards().isEmpty()))) {
            applyDraft(saved, req, user, now);
        }
        return buildDetail(saved, LocalDateTime.now());
    }

    /** 整单全量保存草稿。只有草稿可保存；开始后指令链冻结，一律拒绝。 */
    @Transactional
    public DrillViews.DrillDetailView saveDraft(Long id, DrillDtos.SaveDrillReq req) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(id);
        if (!DrillDict.SESSION_EDITABLE.contains(session.getStatus())) {
            throw new BizException("演练已" + DrillDict.sessionStatusName(session.getStatus())
                    + "，指令链已冻结，不能再编排");
        }
        LocalDateTime now = nowSec();
        applyDraft(session, req, user, now);
        return buildDetail(session, LocalDateTime.now());
    }

    private void applyDraft(DrillSession session, DrillDtos.SaveDrillReq req, SysUser user, LocalDateTime now) {
        if (req == null) {
            throw new BizException("请求体为空");
        }
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BizException("演练名称不能为空");
        }
        if (req.getBuildingId() != null
                && buildingRepository.findById(req.getBuildingId()).isEmpty()) {
            throw new BizException("所选建筑不存在：" + req.getBuildingId());
        }

        // ---- 岗位：编码去重 ----
        List<DrillDtos.PostReq> postReqs = req.getPosts() == null ? List.of() : req.getPosts();
        Map<String, DrillDtos.PostReq> postByCode = new LinkedHashMap<>();
        for (DrillDtos.PostReq p : postReqs) {
            String code = p.getPostCode() == null ? null : p.getPostCode().trim();
            if (code == null || code.isEmpty()) {
                throw new BizException("存在岗位编码为空的岗位");
            }
            if (postByCode.put(code, p) != null) {
                throw new BizException("岗位编码「" + code + "」重复");
            }
        }

        // ---- 指令卡：顺序号去重、目标岗位必须存在、前置引用必须指向本单内的卡 ----
        List<DrillDtos.CardReq> cardReqs = req.getCards() == null ? List.of() : req.getCards();
        Set<Integer> seqs = new HashSet<>();
        Set<String> clientIds = new HashSet<>();
        for (int i = 0; i < cardReqs.size(); i++) {
            DrillDtos.CardReq c = cardReqs.get(i);
            int seq = c.getSeqNo() == null ? i + 1 : c.getSeqNo();
            c.setSeqNo(seq);
            if (!seqs.add(seq)) {
                throw new BizException("指令卡顺序号 " + seq + " 重复");
            }
            if (c.getTimeLimitSeconds() == null) {
                c.setTimeLimitSeconds(60);
            }
            if (c.getTimeLimitSeconds() < 1 || c.getTimeLimitSeconds() > 3600) {
                throw new BizException("第 " + seq + " 张卡的响应时限应在 1~3600 秒之间");
            }
            if (!postByCode.containsKey(c.getTargetPostCode())) {
                throw new BizException("第 " + seq + " 张卡的目标岗位「" + c.getTargetPostCode() + "」不在岗位清单中");
            }
            if (!clientIds.add(c.getClientId())) {
                throw new BizException("指令卡临时标识「" + c.getClientId() + "」重复");
            }
        }
        for (DrillDtos.CardReq c : cardReqs) {
            if (c.getPrereqClientIds() != null) {
                for (String ref : c.getPrereqClientIds()) {
                    if (!clientIds.contains(ref)) {
                        throw new BizException("第 " + c.getSeqNo() + " 张卡引用了不存在的前置节点：" + ref);
                    }
                    if (ref.equals(c.getClientId())) {
                        throw new BizException("第 " + c.getSeqNo() + " 张卡不能把自己设为前置节点");
                    }
                }
            }
        }

        // ---- 全量重建岗位 / 卡 / 依赖（草稿期无事件、无响应，直接重建最干净） ----
        dependencyRepository.deleteBySessionId(session.getId());
        cardRepository.deleteBySessionId(session.getId());
        postRepository.deleteBySessionId(session.getId());
        dependencyRepository.flush();
        cardRepository.flush();
        postRepository.flush();

        Map<String, Long> postIdByCode = new HashMap<>();
        int postSort = 1;
        for (DrillDtos.PostReq p : postReqs) {
            DrillPost post = new DrillPost();
            post.setSessionId(session.getId());
            post.setPostCode(p.getPostCode().trim());
            post.setPostName(p.getPostName().trim());
            post.setAssignee(p.getAssignee());
            post.setContact(p.getContact());
            post.setSortNo(p.getSortNo() == null ? postSort : p.getSortNo());
            post.setCreatedAt(now);
            postRepository.save(post);
            postIdByCode.put(post.getPostCode(), post.getId());
            postSort++;
        }

        // 临时标识 → 持久化卡：先落卡拿 id
        Map<String, DrillCard> cardByClientId = new HashMap<>();
        for (int i = 0; i < cardReqs.size(); i++) {
            DrillDtos.CardReq c = cardReqs.get(i);
            DrillCard card = new DrillCard();
            card.setSessionId(session.getId());
            card.setSeqNo(c.getSeqNo());
            card.setTitle(c.getTitle().trim());
            card.setContent(c.getContent().trim());
            card.setSceneHint(c.getSceneHint());
            card.setTimeLimitSeconds(c.getTimeLimitSeconds());
            card.setStatus(DrillDict.CARD_PENDING);
            card.setTargetPostId(postIdByCode.get(c.getTargetPostCode()));
            card.setCreatedAt(now);
            cardRepository.save(card);
            cardByClientId.put(c.getClientId(), card);
        }
        // 再落依赖
        for (DrillDtos.CardReq c : cardReqs) {
            if (c.getPrereqClientIds() == null) {
                continue;
            }
            DrillCard card = cardByClientId.get(c.getClientId());
            Set<Long> prereqIds = new HashSet<>();
            for (String ref : c.getPrereqClientIds()) {
                DrillCard prereq = cardByClientId.get(ref);
                if (prereq != null && prereqIds.add(prereq.getId())) {
                    DrillCardDependency dep = new DrillCardDependency();
                    dep.setSessionId(session.getId());
                    dep.setCardId(card.getId());
                    dep.setPrereqId(prereq.getId());
                    dep.setCreatedAt(now);
                    dependencyRepository.save(dep);
                }
            }
        }

        session.setTitle(req.getTitle().trim());
        session.setBuildingId(req.getBuildingId());
        session.setScenario(req.getScenario());
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);
    }

    // ==================== 就绪 / 开始 ====================

    @Transactional
    public DrillViews.DrillDetailView ready(Long id, String actionKey) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(id);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, actionKey);
        if (replay != null) {
            return replay;
        }
        if (!DrillDict.SESSION_DRAFT.equals(session.getStatus())) {
            throw new BizException("只有草稿可以标记就绪，当前状态："
                    + DrillDict.sessionStatusName(session.getStatus()));
        }
        List<String> problems = validateStructure(session.getId());
        if (!problems.isEmpty()) {
            throw new BizException("还不能就绪：" + String.join("；", problems));
        }
        LocalDateTime now = nowSec();
        String from = session.getStatus();
        session.setStatus(DrillDict.SESSION_READY);
        session.setReadyAt(now);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);
        writeEvent(id, "READY", "SESSION", id, actionKey,
                from, DrillDict.SESSION_READY, "编排完成，演练就绪，等待开始", user.getDisplayName(), now);
        return buildDetail(session, LocalDateTime.now());
    }

    /** 就绪后允许退回草稿继续编排（开始后冻结，不可再退）。 */
    @Transactional
    public DrillViews.DrillDetailView reopen(Long id, String actionKey) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(id);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, actionKey);
        if (replay != null) {
            return replay;
        }
        if (!DrillDict.SESSION_READY.equals(session.getStatus())) {
            throw new BizException("只有就绪的演练可以退回草稿，当前状态："
                    + DrillDict.sessionStatusName(session.getStatus()));
        }
        LocalDateTime now = nowSec();
        String from = session.getStatus();
        session.setStatus(DrillDict.SESSION_DRAFT);
        session.setReadyAt(null);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);
        writeEvent(id, "REOPEN", "SESSION", id, actionKey,
                from, DrillDict.SESSION_DRAFT, "退回草稿继续编排", user.getDisplayName(), now);
        return buildDetail(session, LocalDateTime.now());
    }

    /**
     * 开始演练：冻结本场指令链（目标岗位编码/名称快照写进每张卡），
     * 此后改脚本、改草稿都与本场无关。
     */
    @Transactional
    public DrillViews.DrillDetailView start(Long id, String actionKey) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(id);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, actionKey);
        if (replay != null) {
            return replay;
        }
        if (!DrillDict.SESSION_READY.equals(session.getStatus())) {
            throw new BizException("只有就绪的演练可以开始，当前状态："
                    + DrillDict.sessionStatusName(session.getStatus()));
        }
        List<DrillPost> posts = postRepository.findBySessionIdOrderBySortNoAscIdAsc(id);
        Map<Long, DrillPost> postById = new HashMap<>();
        posts.forEach(p -> postById.put(p.getId(), p));
        LocalDateTime now = nowSec();
        for (DrillCard card : cardRepository.findBySessionIdOrderBySeqNoAscIdAsc(id)) {
            DrillPost post = postById.get(card.getTargetPostId());
            if (post == null) {
                throw new BizException("第 " + card.getSeqNo() + " 张卡的目标岗位缺失，请回到编排页补齐");
            }
            card.setTargetPostCode(post.getPostCode());
            card.setTargetPostName(post.getPostName());
            cardRepository.save(card);
        }
        String from = session.getStatus();
        session.setStatus(DrillDict.SESSION_RUNNING);
        session.setStartedAt(now);
        session.setPausedAt(null);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);
        writeEvent(id, "START", "SESSION", id, actionKey,
                from, DrillDict.SESSION_RUNNING,
                "开始演练，本场指令链（" + posts.size() + " 岗）已冻结",
                user.getDisplayName(), now);
        return buildDetail(session, LocalDateTime.now());
    }

    // ==================== 暂停 / 恢复 / 取消 / 结束 ====================

    @Transactional
    public DrillViews.DrillDetailView pause(Long id, String actionKey) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(id);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, actionKey);
        if (replay != null) {
            return replay;
        }
        if (!DrillDict.SESSION_PAUSABLE.contains(session.getStatus())) {
            throw new BizException("只有进行中的演练可以暂停，当前状态："
                    + DrillDict.sessionStatusName(session.getStatus()));
        }
        LocalDateTime now = nowSec();
        sweepTimeouts(session, now);
        // 若全部等待卡恰好在这一刻超时，仍允许暂停（场次状态推进为暂停）
        int frozen = 0;
        for (DrillCard card : cardRepository.findBySessionIdOrderBySeqNoAscIdAsc(id)) {
            if (DrillDict.CARD_WAITING.equals(card.getStatus()) && card.getDeadline() != null) {
                int remain = (int) Math.max(0, ChronoUnit.SECONDS.between(now, card.getDeadline()));
                card.setRemainingFrozenSeconds(remain);
                card.setDeadline(null);
                cardRepository.save(card);
                frozen++;
            }
        }
        String from = session.getStatus();
        session.setStatus(DrillDict.SESSION_PAUSED);
        session.setPausedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);
        writeEvent(id, "PAUSE", "SESSION", id, actionKey,
                from, DrillDict.SESSION_PAUSED,
                "暂停演练，" + frozen + " 张等待响应卡的剩余时间已冻结", user.getDisplayName(), now);
        return buildDetail(session, LocalDateTime.now());
    }

    @Transactional
    public DrillViews.DrillDetailView resume(Long id, String actionKey) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(id);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, actionKey);
        if (replay != null) {
            return replay;
        }
        if (!DrillDict.SESSION_RESUMABLE.contains(session.getStatus())) {
            throw new BizException("只有暂停的演练可以恢复，当前状态："
                    + DrillDict.sessionStatusName(session.getStatus()));
        }
        LocalDateTime now = nowSec();
        int resumed = 0;
        for (DrillCard card : cardRepository.findBySessionIdOrderBySeqNoAscIdAsc(id)) {
            if (DrillDict.CARD_WAITING.equals(card.getStatus())
                    && card.getRemainingFrozenSeconds() != null) {
                card.setDeadline(now.plusSeconds(card.getRemainingFrozenSeconds()));
                card.setRemainingFrozenSeconds(null);
                cardRepository.save(card);
                resumed++;
            }
        }
        String from = session.getStatus();
        session.setStatus(DrillDict.SESSION_RUNNING);
        session.setPausedAt(null);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);
        writeEvent(id, "RESUME", "SESSION", id, actionKey,
                from, DrillDict.SESSION_RUNNING,
                "恢复演练，" + resumed + " 张卡从冻结的剩余秒数继续倒计时", user.getDisplayName(), now);
        return buildDetail(session, LocalDateTime.now());
    }

    @Transactional
    public DrillViews.DrillDetailView cancel(Long id, DrillDtos.CancelReq req) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(id);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, req.getActionKey());
        if (replay != null) {
            return replay;
        }
        if (!DrillDict.SESSION_CANCELABLE.contains(session.getStatus())) {
            throw new BizException("当前状态不能取消：" + DrillDict.sessionStatusName(session.getStatus()));
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new BizException("取消演练必须填写原因");
        }
        LocalDateTime now = nowSec();
        String from = session.getStatus();
        session.setStatus(DrillDict.SESSION_CANCELED);
        session.setCanceledAt(now);
        session.setCanceledBy(user.getDisplayName());
        session.setCancelReason(req.getReason().trim());
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);
        writeEvent(id, "CANCEL", "SESSION", id, req.getActionKey(),
                from, DrillDict.SESSION_CANCELED, "取消演练：" + req.getReason().trim(),
                user.getDisplayName(), now);
        return buildDetail(session, LocalDateTime.now());
    }

    /** 结束演练：所有卡必须处于明确终态，且每条超时 / 跳过记录都已填写处置结论。 */
    @Transactional
    public DrillViews.DrillDetailView finish(Long id, DrillDtos.KeyedReq req) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(id);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, req.getActionKey());
        if (replay != null) {
            return replay;
        }
        if (!DrillDict.SESSION_FINISHABLE.contains(session.getStatus())) {
            throw new BizException("只有进行中或暂停的演练可以结束，当前状态："
                    + DrillDict.sessionStatusName(session.getStatus()));
        }
        LocalDateTime now = nowSec();
        sweepTimeouts(session, now);

        List<DrillCard> cards = cardRepository.findBySessionIdOrderBySeqNoAscIdAsc(id);
        List<String> blockers = finishBlockers(cards);
        if (!blockers.isEmpty()) {
            throw new BizException("还不能结束演练：" + String.join("；", blockers));
        }

        String from = session.getStatus();
        session.setStatus(DrillDict.SESSION_FINISHED);
        session.setFinishedAt(now);
        session.setFinishedBy(user.getDisplayName());
        session.setPausedAt(null);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);
        writeEvent(id, "FINISH", "SESSION", id, req.getActionKey(),
                from, DrillDict.SESSION_FINISHED,
                "全部 " + cards.size() + " 张指令卡均已到终态且处置结论齐全，演练结束",
                user.getDisplayName(), now);
        return buildDetail(session, LocalDateTime.now());
    }

    // ==================== 指令卡：下达 / 确认 / 跳过 / 处置 ====================

    @Transactional
    public DrillViews.DrillDetailView issueCard(Long sessionId, Long cardId, DrillDtos.KeyedReq req) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(sessionId);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, req.getActionKey());
        if (replay != null) {
            return replay;
        }
        LocalDateTime now = nowSec();
        sweepTimeouts(session, now);
        DrillCard card = ensureCard(sessionId, cardId);
        if (DrillDict.CARD_WAITING.equals(card.getStatus())
                || DrillDict.isCardTerminal(card.getStatus())) {
            throw new BizException("该指令卡当前为「" + DrillDict.cardStatusName(card.getStatus()) + "」，不能重复下达");
        }
        if (!DrillDict.SESSION_RUNNING.equals(session.getStatus())) {
            throw new BizException("只有进行中的演练可以下达指令，当前状态："
                    + DrillDict.sessionStatusName(session.getStatus()));
        }
        if (!DrillDict.CARD_PENDING.equals(card.getStatus())) {
            throw new BizException("该指令卡当前为「" + DrillDict.cardStatusName(card.getStatus())
                    + "」，不能下达");
        }
        List<DrillViews.BlockerView> blockers = blockersOf(sessionId, cardId);
        if (!blockers.isEmpty()) {
            throw new BizException("前置节点尚未全部确认，不能下达："
                    + blockers.stream().map(DrillViews.BlockerView::reason)
                            .reduce((a, b) -> a + "；" + b).orElse(""));
        }

        String from = card.getStatus();
        card.setStatus(DrillDict.CARD_WAITING);
        card.setIssuedAt(now);
        card.setIssuedBy(user.getDisplayName());
        card.setDeadline(now.plusSeconds(card.getTimeLimitSeconds()));
        card.setRemainingFrozenSeconds(null);
        cardRepository.saveAndFlush(card);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);
        writeEvent(sessionId, "ISSUE", "CARD", cardId, req.getActionKey(),
                from, DrillDict.CARD_WAITING,
                "下达 #" + card.getSeqNo() + "「" + card.getTitle() + "」→ "
                        + card.getTargetPostName() + "，响应时限 " + card.getTimeLimitSeconds() + " 秒",
                user.getDisplayName(), now);
        return buildDetail(session, LocalDateTime.now());
    }

    @Transactional
    public DrillViews.DrillDetailView confirmCard(Long sessionId, Long cardId, DrillDtos.ConfirmReq req) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(sessionId);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, req.getActionKey());
        if (replay != null) {
            return replay;
        }
        LocalDateTime now = nowSec();
        sweepTimeouts(session, now);
        DrillCard card = ensureCard(sessionId, cardId);

        if (req.getActualAction() == null || req.getActualAction().isBlank()) {
            throw new BizException("岗位确认必须填写实际动作");
        }

        // 无幂等键的重复提交才拦截（同动作标识的重发已在上面回放）
        if (DrillDict.CARD_CONFIRMED.equals(card.getStatus())) {
            throw new BizException("该指令已确认，请勿重复提交");
        }
        if (!DrillDict.SESSION_RUNNING.equals(session.getStatus())
                && !DrillDict.SESSION_PAUSED.equals(session.getStatus())) {
            throw new BizException("演练当前为「" + DrillDict.sessionStatusName(session.getStatus())
                    + "」，不能提交确认");
        }
        if (DrillDict.CARD_PENDING.equals(card.getStatus())) {
            throw new BizException("该指令尚未下达，不能确认");
        }

        // 记录岗位响应尝试（无论按时还是迟到，都保留）
        DrillResponseAttempt attempt = new DrillResponseAttempt();
        attempt.setSessionId(sessionId);
        attempt.setCardId(cardId);
        attempt.setPostId(card.getTargetPostId());
        attempt.setActualAction(req.getActualAction().trim());
        attempt.setRemark(req.getRemark());
        attempt.setOperator(user.getDisplayName());
        attempt.setCreatedAt(now);

        String cardRef = "#" + card.getSeqNo() + "「" + card.getTitle() + "」";
        if (DrillDict.CARD_WAITING.equals(card.getStatus())) {
            if (DrillDict.SESSION_PAUSED.equals(session.getStatus())) {
                throw new BizException("演练已暂停，倒计时冻结中，请恢复后再提交确认");
            }
            attempt.setResult(DrillDict.ATTEMPT_CONFIRMED);
            attemptRepository.save(attempt);
            String from = card.getStatus();
            card.setStatus(DrillDict.CARD_CONFIRMED);
            card.setConfirmedAt(now);
            card.setConfirmedBy(user.getDisplayName());
            card.setActualAction(req.getActualAction().trim());
            card.setConfirmRemark(req.getRemark());
            card.setDeadline(null);
            card.setRemainingFrozenSeconds(null);
            cardRepository.saveAndFlush(card);
            session.setUpdatedAt(now);
            sessionRepository.saveAndFlush(session);
            writeEvent(sessionId, "CONFIRM", "CARD", cardId, req.getActionKey(),
                    from, DrillDict.CARD_CONFIRMED,
                    cardRef + " 按时确认，实际动作：" + req.getActualAction().trim()
                            + (req.getRemark() != null && !req.getRemark().isBlank()
                            ? "；备注：" + req.getRemark() : ""),
                    user.getDisplayName(), now);
        } else {
            // TIMEOUT / SKIPPED：迟到尝试不改终态，只追加
            attempt.setResult(DrillDict.ATTEMPT_LATE);
            attemptRepository.save(attempt);
            session.setUpdatedAt(now);
            sessionRepository.saveAndFlush(session);
            String terminalName = DrillDict.cardStatusName(card.getStatus());
            writeEvent(sessionId, "LATE_CONFIRM", "CARD", cardId, req.getActionKey(),
                    card.getStatus(), card.getStatus(),
                    cardRef + " 已" + terminalName + "，本次到达的确认作为迟到尝试保留，终态不变；实际动作："
                            + req.getActualAction().trim(),
                    user.getDisplayName(), now);
        }
        return buildDetail(session, LocalDateTime.now());
    }

    @Transactional
    public DrillViews.DrillDetailView skipCard(Long sessionId, Long cardId, DrillDtos.SkipReq req) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(sessionId);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, req.getActionKey());
        if (replay != null) {
            return replay;
        }
        LocalDateTime now = nowSec();
        sweepTimeouts(session, now);
        DrillCard card = ensureCard(sessionId, cardId);
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new BizException("跳过节点必须填写原因");
        }
        if (DrillDict.CARD_SKIPPED.equals(card.getStatus())) {
            throw new BizException("该节点已跳过，请勿重复操作");
        }
        if (DrillDict.CARD_CONFIRMED.equals(card.getStatus())) {
            throw new BizException("已确认的节点不能跳过");
        }
        if (DrillDict.CARD_TIMEOUT.equals(card.getStatus())) {
            throw new BizException("已超时的节点终态为超时，不能改成跳过；如需豁免请在处置结论中说明");
        }
        if (!DrillDict.SESSION_RUNNING.equals(session.getStatus())
                && !DrillDict.SESSION_PAUSED.equals(session.getStatus())) {
            throw new BizException("演练当前为「" + DrillDict.sessionStatusName(session.getStatus())
                    + "」，不能跳过节点");
        }
        String from = card.getStatus();
        card.setStatus(DrillDict.CARD_SKIPPED);
        card.setSkippedAt(now);
        card.setSkippedBy(user.getDisplayName());
        card.setSkipReason(req.getReason().trim());
        card.setDeadline(null);
        card.setRemainingFrozenSeconds(null);
        cardRepository.saveAndFlush(card);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);

        // 依赖该节点的卡：事件说明里点名阻塞来源，前端卡面同时展示，不能悄悄越过
        List<DrillCard> blocked = dependentPendingCards(sessionId, cardId);
        String note = "跳过 #" + card.getSeqNo() + "「" + card.getTitle() + "」，原因：" + req.getReason().trim();
        if (!blocked.isEmpty()) {
            note += "；下游卡 " + blocked.stream().map(c -> "#" + c.getSeqNo())
                    .reduce((a, b) -> a + "、" + b).orElse("") + " 将显示被该节点阻塞，需逐张跳过或调整";
        }
        writeEvent(sessionId, "SKIP", "CARD", cardId, req.getActionKey(),
                from, DrillDict.CARD_SKIPPED, note, user.getDisplayName(), now);
        return buildDetail(session, LocalDateTime.now());
    }

    @Transactional
    public DrillViews.DrillDetailView disposeCard(Long sessionId, Long cardId, DrillDtos.DisposeReq req) {
        SysUser user = CurrentUserHolder.require();
        DrillSession session = sessionRepository.lockById(sessionId);
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, req.getActionKey());
        if (replay != null) {
            return replay;
        }
        DrillCard card = ensureCard(sessionId, cardId);
        if (req.getDisposition() == null || req.getDisposition().isBlank()) {
            throw new BizException("处置结论不能为空");
        }
        if (!DrillDict.CARD_TIMEOUT.equals(card.getStatus())
                && !DrillDict.CARD_SKIPPED.equals(card.getStatus())) {
            throw new BizException("只有已超时或已跳过的记录需要填写处置结论，当前状态："
                    + DrillDict.cardStatusName(card.getStatus()));
        }
        LocalDateTime now = nowSec();
        boolean firstFill = card.getDisposition() == null;
        card.setDisposition(req.getDisposition().trim());
        card.setDispositionAt(now);
        card.setDispositionBy(user.getDisplayName());
        cardRepository.saveAndFlush(card);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);
        writeEvent(sessionId, "DISPOSE", "CARD", cardId, req.getActionKey(),
                card.getStatus(), card.getStatus(),
                (firstFill ? "填写 #" : "补充 #") + card.getSeqNo() + "「" + card.getTitle()
                        + "」处置结论：" + req.getDisposition().trim(),
                user.getDisplayName(), now);
        return buildDetail(session, LocalDateTime.now());
    }

    // ==================== 超时扫描 ====================

    /**
     * 服务端定时扫描：不依赖任何浏览器。进行中场次里 deadline 已到的等待卡判超时。
     * 每次卡片动作前也会在同一事务里调 {@link #sweepTimeouts} 兜底，
     * 保证暂停 / 重启后口径一致。
     */
    @Scheduled(fixedDelayString = "${drill.timeout.sweep-ms:3000}")
    @Transactional
    public void scheduledSweep() {
        LocalDateTime now = nowSec();
        List<DrillCard> due = cardRepository.findTimedOut(now);
        if (due.isEmpty()) {
            return;
        }
        Set<Long> sessionIds = new HashSet<>();
        due.forEach(c -> sessionIds.add(c.getSessionId()));
        for (Long sid : sessionIds) {
            try {
                DrillSession locked = sessionRepository.lockById(sid);
                // 只处理进行中场次：暂停卡 deadline 已清空（本就查不出），多一道状态保险
                if (DrillDict.SESSION_RUNNING.equals(locked.getStatus())) {
                    sweepTimeouts(locked, now);
                }
            } catch (PessimisticLockingFailureException e) {
                // 该场次正被其它动作事务持锁：本轮跳过，下一个 3 秒扫描周期自然补上
            }
        }
    }

    /** 在已持有场次行锁的事务内，把本场到期的等待卡翻成超时并写事件（调用方必须已 lockById）。 */
    private void sweepTimeouts(DrillSession session, LocalDateTime now) {
        if (!DrillDict.SESSION_RUNNING.equals(session.getStatus())) {
            return;
        }
        boolean changed = false;
        for (DrillCard card : cardRepository.findBySessionIdOrderBySeqNoAscIdAsc(session.getId())) {
            if (DrillDict.CARD_WAITING.equals(card.getStatus())
                    && card.getDeadline() != null && !card.getDeadline().isAfter(now)) {
                card.setStatus(DrillDict.CARD_TIMEOUT);
                card.setTimedOutAt(now);
                card.setDeadline(null);
                card.setRemainingFrozenSeconds(null);
                cardRepository.save(card);
                changed = true;
                List<DrillCard> blocked = dependentPendingCards(session.getId(), card.getId());
                String note = "#" + card.getSeqNo() + "「" + card.getTitle() + "」响应超过 "
                        + card.getTimeLimitSeconds() + " 秒未确认，系统判定超时";
                if (!blocked.isEmpty()) {
                    note += "；下游卡 " + blocked.stream().map(c -> "#" + c.getSeqNo())
                            .reduce((a, b) -> a + "、" + b).orElse("") + " 被阻塞";
                }
                writeEvent(session.getId(), "TIMEOUT", "CARD", card.getId(), null,
                        DrillDict.CARD_WAITING, DrillDict.CARD_TIMEOUT, note, "系统", now);
            }
        }
        if (changed) {
            session.setUpdatedAt(now);
            sessionRepository.saveAndFlush(session);
        }
    }

    // ==================== 常用脚本 ====================

    @Transactional(readOnly = true)
    public List<DrillViews.TemplateListItem> templateList() {
        return templateRepository.findAllByOrderByIdDesc().stream()
                .map(t -> new DrillViews.TemplateListItem(t.getId(), t.getName(), t.getDescription(),
                        t.getCreatedBy(),
                        templatePostRepository.findByTemplateIdOrderBySortNoAscIdAsc(t.getId()).size(),
                        templateCardRepository.findByTemplateIdOrderBySeqNoAscIdAsc(t.getId()).size(),
                        t.getCreatedAt(), t.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DrillViews.TemplateDetailView templateDetail(Long id) {
        DrillTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new BizException("常用脚本不存在：" + id));
        return toTemplateDetail(t);
    }

    @Transactional
    public DrillViews.TemplateDetailView saveTemplate(DrillDtos.SaveTemplateReq req) {
        SysUser user = CurrentUserHolder.require();
        LocalDateTime now = nowSec();
        validateTemplateReq(req);

        DrillTemplate template;
        if (req.getId() != null) {
            template = templateRepository.findById(req.getId())
                    .orElseThrow(() -> new BizException("常用脚本不存在：" + req.getId()));
            if (!template.getName().equals(req.getName().trim())
                    && templateRepository.existsByName(req.getName().trim())) {
                throw new BizException("脚本名称「" + req.getName().trim() + "」已存在");
            }
            template.setName(req.getName().trim());
            template.setDescription(req.getDescription());
            template.setUpdatedAt(now);
            templateCardRepository.deleteByTemplateId(template.getId());
            templatePostRepository.deleteByTemplateId(template.getId());
            templateCardRepository.flush();
            templatePostRepository.flush();
        } else {
            if (templateRepository.existsByName(req.getName().trim())) {
                throw new BizException("脚本名称「" + req.getName().trim() + "」已存在");
            }
            template = new DrillTemplate();
            template.setName(req.getName().trim());
            template.setDescription(req.getDescription());
            template.setCreatedBy(user.getDisplayName());
            template.setCreatedAt(now);
            template.setUpdatedAt(now);
            template = templateRepository.saveAndFlush(template);
        }

        int sort = 1;
        for (DrillDtos.PostReq p : req.getPosts()) {
            DrillTemplatePost tp = new DrillTemplatePost();
            tp.setTemplateId(template.getId());
            tp.setPostCode(p.getPostCode().trim());
            tp.setPostName(p.getPostName().trim());
            tp.setSortNo(p.getSortNo() == null ? sort : p.getSortNo());
            templatePostRepository.save(tp);
            sort++;
        }
        Set<String> codes = req.getPosts().stream()
                .map(p -> p.getPostCode().trim()).collect(java.util.stream.Collectors.toSet());
        for (int i = 0; i < req.getCards().size(); i++) {
            DrillDtos.TemplateCardReq c = req.getCards().get(i);
            if (!codes.contains(c.getTargetPostCode())) {
                throw new BizException("第 " + c.getSeqNo() + " 张卡的目标岗位不在脚本岗位中");
            }
            DrillTemplateCard tc = new DrillTemplateCard();
            tc.setTemplateId(template.getId());
            tc.setSeqNo(c.getSeqNo() == null ? i + 1 : c.getSeqNo());
            tc.setTitle(c.getTitle().trim());
            tc.setContent(c.getContent().trim());
            tc.setSceneHint(c.getSceneHint());
            tc.setTargetPostCode(c.getTargetPostCode());
            tc.setTimeLimitSeconds(c.getTimeLimitSeconds() == null ? 60 : c.getTimeLimitSeconds());
            tc.setPrereqSeqs(c.getPrereqSeqs() == null || c.getPrereqSeqs().isEmpty()
                    ? null
                    : c.getPrereqSeqs().stream().map(String::valueOf)
                            .reduce((a, b) -> a + "," + b).orElse(null));
            templateCardRepository.save(tc);
        }
        return toTemplateDetail(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        CurrentUserHolder.require();
        DrillTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new BizException("常用脚本不存在：" + id));
        templateCardRepository.deleteByTemplateId(id);
        templatePostRepository.deleteByTemplateId(id);
        templateRepository.delete(template);
    }

    private void validateTemplateReq(DrillDtos.SaveTemplateReq req) {
        if (req == null) {
            throw new BizException("请求体为空");
        }
        if (req.getPosts() == null || req.getPosts().isEmpty()) {
            throw new BizException("脚本至少要有一个岗位");
        }
        if (req.getCards() == null || req.getCards().isEmpty()) {
            throw new BizException("脚本至少要有一张指令卡");
        }
        Set<String> codes = new HashSet<>();
        Set<Integer> seqs = new HashSet<>();
        for (DrillDtos.PostReq p : req.getPosts()) {
            if (!codes.add(p.getPostCode().trim())) {
                throw new BizException("脚本岗位编码「" + p.getPostCode() + "」重复");
            }
        }
        for (DrillDtos.TemplateCardReq c : req.getCards()) {
            int seq = c.getSeqNo() == null ? 0 : c.getSeqNo();
            if (!seqs.add(seq)) {
                throw new BizException("脚本指令卡顺序号 " + seq + " 重复");
            }
            if (c.getTimeLimitSeconds() != null
                    && (c.getTimeLimitSeconds() < 1 || c.getTimeLimitSeconds() > 3600)) {
                throw new BizException("第 " + seq + " 张卡的响应时限应在 1~3600 秒之间");
            }
        }
    }

    // ==================== 详情组装 ====================

    private DrillViews.DrillDetailView buildDetail(DrillSession session, LocalDateTime serverNow) {
        LocalDateTime now = nowSec(serverNow);
        Long sid = session.getId();
        List<DrillPost> posts = postRepository.findBySessionIdOrderBySortNoAscIdAsc(sid);
        List<DrillCard> cards = cardRepository.findBySessionIdOrderBySeqNoAscIdAsc(sid);
        List<DrillCardDependency> deps = dependencyRepository.findBySessionIdOrderByIdAsc(sid);
        List<DrillResponseAttempt> attempts = attemptRepository.findBySessionIdOrderByIdAsc(sid);
        List<DrillEvent> events = eventRepository.findBySessionIdOrderByIdAsc(sid);

        Map<Long, DrillPost> postById = new HashMap<>();
        posts.forEach(p -> postById.put(p.getId(), p));
        Map<Long, DrillCard> cardById = new HashMap<>();
        cards.forEach(c -> cardById.put(c.getId(), c));

        // 前置关系
        Map<Long, List<Long>> prereqIdsByCard = new HashMap<>();
        for (DrillCardDependency d : deps) {
            prereqIdsByCard.computeIfAbsent(d.getCardId(), k -> new ArrayList<>()).add(d.getPrereqId());
        }

        boolean running = DrillDict.SESSION_RUNNING.equals(session.getStatus());
        List<DrillViews.CardView> cardViews = new ArrayList<>();
        Map<Long, List<DrillViews.AttemptView>> attemptListByCard = new HashMap<>();
        for (DrillResponseAttempt a : attempts) {
            DrillCard c = cardById.get(a.getCardId());
            DrillViews.AttemptView av = new DrillViews.AttemptView(
                    a.getId(), a.getCardId(), c == null ? null : c.getSeqNo(),
                    c == null ? "" : c.getTitle(), a.getPostId(),
                    a.getResult(), DrillDict.attemptResultName(a.getResult()),
                    a.getActualAction(), a.getRemark(), a.getOperator(), a.getCreatedAt(),
                    DrillDict.ATTEMPT_LATE.equals(a.getResult()));
            attemptListByCard.computeIfAbsent(a.getCardId(), k -> new ArrayList<>()).add(av);
        }

        List<Integer> currentSeqs = new ArrayList<>();
        List<Integer> nextSeqs = new ArrayList<>();
        boolean anyNextMarked = false;

        for (DrillCard card : cards) {
            List<Long> prereqIds = prereqIdsByCard.getOrDefault(card.getId(), List.of());
            List<DrillViews.BlockerView> blockers = new ArrayList<>();
            boolean met = true;
            for (Long pid : prereqIds) {
                DrillCard pre = cardById.get(pid);
                if (pre == null) {
                    continue;
                }
                if (!DrillDict.CARD_CONFIRMED.equals(pre.getStatus())) {
                    met = false;
                    String reason = switch (pre.getStatus()) {
                        case DrillDict.CARD_SKIPPED ->
                                "前置 #" + pre.getSeqNo() + "「" + pre.getTitle() + "」已跳过"
                                        + (pre.getSkipReason() != null ? "（原因：" + pre.getSkipReason() + "）" : "");
                        case DrillDict.CARD_TIMEOUT ->
                                "前置 #" + pre.getSeqNo() + "「" + pre.getTitle() + "」已超时"
                                        + (pre.getDisposition() != null ? "（已处置：" + pre.getDisposition() + "）" : "（尚未处置）");
                        case DrillDict.CARD_WAITING ->
                                "前置 #" + pre.getSeqNo() + "「" + pre.getTitle() + "」尚在等待响应";
                        default ->
                                "前置 #" + pre.getSeqNo() + "「" + pre.getTitle() + "」尚未下达确认";
                    };
                    blockers.add(new DrillViews.BlockerView(
                            pre.getId(), pre.getSeqNo(), pre.getTitle(),
                            pre.getTargetPostName(), pre.getStatus(),
                            DrillDict.cardStatusName(pre.getStatus()), reason));
                }
            }

            Integer remaining = null;
            if (DrillDict.CARD_WAITING.equals(card.getStatus())) {
                if (running && card.getDeadline() != null) {
                    remaining = (int) Math.max(0, ChronoUnit.SECONDS.between(now, card.getDeadline()));
                } else if (card.getRemainingFrozenSeconds() != null) {
                    remaining = card.getRemainingFrozenSeconds();
                }
                currentSeqs.add(card.getSeqNo());
            }

            List<Integer> prereqSeqs = prereqIds.stream()
                    .map(pid -> cardById.get(pid))
                    .filter(java.util.Objects::nonNull)
                    .map(DrillCard::getSeqNo).sorted().toList();

            boolean issuable = running
                    && DrillDict.CARD_PENDING.equals(card.getStatus()) && met;
            if (issuable) {
                nextSeqs.add(card.getSeqNo());
            }

            DrillViews.CardView cv = new DrillViews.CardView(
                    card.getId(), card.getSeqNo(), card.getTitle(), card.getContent(),
                    card.getSceneHint(), card.getTargetPostId(), card.getTargetPostCode(),
                    card.getTargetPostName(), card.getTimeLimitSeconds(),
                    card.getStatus(), DrillDict.cardStatusName(card.getStatus()),
                    DrillDict.isCardTerminal(card.getStatus()),
                    card.getIssuedAt(), card.getIssuedBy(), card.getDeadline(),
                    remaining, card.getRemainingFrozenSeconds(),
                    card.getConfirmedAt(), card.getConfirmedBy(), card.getActualAction(),
                    card.getConfirmRemark(), card.getTimedOutAt(),
                    card.getSkippedAt(), card.getSkippedBy(), card.getSkipReason(),
                    card.getDisposition(), card.getDispositionAt(), card.getDispositionBy(),
                    card.getDisposition() != null && !card.getDisposition().isBlank(),
                    prereqSeqs, blockers, met, issuable,
                    false, attemptListByCard.getOrDefault(card.getId(), List.of()));
            cardViews.add(cv);
        }

        // 「下一可执行节点」：可下达卡中顺序最靠前的一张
        int nextSeq = nextSeqs.isEmpty() ? Integer.MAX_VALUE
                : nextSeqs.stream().min(Integer::compareTo).orElse(Integer.MAX_VALUE);
        List<DrillViews.CardView> finalCardViews = new ArrayList<>();
        for (DrillViews.CardView cv : cardViews) {
            if (cv.issuable() && cv.seqNo() == nextSeq) {
                finalCardViews.add(withNextFlag(cv));
                anyNextMarked = true;
            } else {
                finalCardViews.add(cv);
            }
        }

        // 岗位视图（每岗独立的响应记录）
        List<DrillViews.PostView> postViews = new ArrayList<>();
        for (DrillPost post : posts) {
            List<DrillViews.CardView> myCards = finalCardViews.stream()
                    .filter(cv -> post.getId().equals(cv.targetPostId())).toList();
            long waiting = myCards.stream().filter(c -> DrillDict.CARD_WAITING.equals(c.status())).count();
            long confirmed = myCards.stream().filter(c -> DrillDict.CARD_CONFIRMED.equals(c.status())).count();
            long timeout = myCards.stream().filter(c -> DrillDict.CARD_TIMEOUT.equals(c.status())).count();
            long skipped = myCards.stream().filter(c -> DrillDict.CARD_SKIPPED.equals(c.status())).count();
            long pending = myCards.stream().filter(c -> DrillDict.CARD_PENDING.equals(c.status())).count();
            List<DrillViews.AttemptView> myAttempts = attempts.stream()
                    .filter(a -> post.getId().equals(a.getPostId()))
                    .map(a -> {
                        DrillCard c = cardById.get(a.getCardId());
                        return new DrillViews.AttemptView(
                                a.getId(), a.getCardId(), c == null ? null : c.getSeqNo(),
                                c == null ? "" : c.getTitle(), a.getPostId(),
                                a.getResult(), DrillDict.attemptResultName(a.getResult()),
                                a.getActualAction(), a.getRemark(), a.getOperator(), a.getCreatedAt(),
                                DrillDict.ATTEMPT_LATE.equals(a.getResult()));
                    }).toList();
            postViews.add(new DrillViews.PostView(
                    post.getId(), post.getPostCode(), post.getPostName(),
                    post.getAssignee(), post.getContact(), post.getSortNo(),
                    myCards.size(), waiting, confirmed, timeout, skipped, pending,
                    myCards, myAttempts));
        }

        List<DrillViews.EventView> eventViews = events.stream().map(e -> {
            DrillCard c = e.getTargetId() == null ? null : cardById.get(e.getTargetId());
            return new DrillViews.EventView(
                    e.getId(), e.getAction(), DrillDict.actionName(e.getAction()),
                    e.getTargetType(), e.getTargetId(),
                    c == null ? null : c.getSeqNo(),
                    c == null ? null : c.getTitle(),
                    e.getFromStatus(), DrillDict.anyStatusName(e.getFromStatus()),
                    e.getToStatus(), DrillDict.anyStatusName(e.getToStatus()),
                    e.getOperator(), e.getNote(), e.getCreatedAt());
        }).toList();

        // 结束缺项（未结束时也实时给前端，用于按钮旁的缺项提示）
        List<String> blockers = finishBlockers(cards);

        Building building = session.getBuildingId() == null ? null
                : buildingRepository.findById(session.getBuildingId()).orElse(null);

        return new DrillViews.DrillDetailView(
                session.getId(), session.getDrillNo(), session.getTitle(),
                session.getBuildingId(), building == null ? "" : building.getName(),
                session.getScenario(), session.getOrganizer(),
                session.getStatus(), DrillDict.sessionStatusName(session.getStatus()),
                DrillDict.SESSION_EDITABLE.contains(session.getStatus()),
                DrillDict.SESSION_READY.equals(session.getStatus()),
                session.getReadyAt(), session.getStartedAt(), session.getPausedAt(),
                session.getFinishedAt(), session.getFinishedBy(),
                session.getCanceledAt(), session.getCanceledBy(), session.getCancelReason(),
                session.getVersion(), session.getCreatedAt(), session.getUpdatedAt(),
                now, finalCardViews, currentSeqs,
                anyNextMarked ? List.of(nextSeq) : List.of(),
                postViews, eventViews, blockers);
    }

    private DrillViews.CardView withNextFlag(DrillViews.CardView cv) {
        return new DrillViews.CardView(
                cv.id(), cv.seqNo(), cv.title(), cv.content(), cv.sceneHint(),
                cv.targetPostId(), cv.targetPostCode(), cv.targetPostName(),
                cv.timeLimitSeconds(), cv.status(), cv.statusName(), cv.terminal(),
                cv.issuedAt(), cv.issuedBy(), cv.deadline(), cv.remainingSeconds(),
                cv.remainingFrozenSeconds(), cv.confirmedAt(), cv.confirmedBy(),
                cv.actualAction(), cv.confirmRemark(), cv.timedOutAt(),
                cv.skippedAt(), cv.skippedBy(), cv.skipReason(),
                cv.disposition(), cv.dispositionAt(), cv.dispositionBy(), cv.disposed(),
                cv.prereqSeqs(), cv.blockers(), cv.prerequisitesMet(), cv.issuable(), true,
                cv.attempts());
    }

    private DrillViews.TemplateDetailView toTemplateDetail(DrillTemplate t) {
        List<DrillViews.TemplatePostView> posts = templatePostRepository
                .findByTemplateIdOrderBySortNoAscIdAsc(t.getId()).stream()
                .map(p -> new DrillViews.TemplatePostView(p.getPostCode(), p.getPostName(), p.getSortNo()))
                .toList();
        List<DrillViews.TemplateCardView> cards = templateCardRepository
                .findByTemplateIdOrderBySeqNoAscIdAsc(t.getId()).stream()
                .map(c -> {
                    List<Integer> seqs = new ArrayList<>();
                    if (c.getPrereqSeqs() != null && !c.getPrereqSeqs().isBlank()) {
                        for (String part : c.getPrereqSeqs().split(",")) {
                            try {
                                seqs.add(Integer.parseInt(part.trim()));
                            } catch (NumberFormatException ignored) {
                                // 脏数据忽略
                            }
                        }
                    }
                    return new DrillViews.TemplateCardView(c.getSeqNo(), c.getTitle(), c.getContent(),
                            c.getSceneHint(), c.getTargetPostCode(), c.getTimeLimitSeconds(), seqs);
                }).toList();
        return new DrillViews.TemplateDetailView(t.getId(), t.getName(), t.getDescription(),
                t.getCreatedBy(), t.getCreatedAt(), t.getUpdatedAt(), posts, cards);
    }

    private DrillViews.DrillListItem toListItem(DrillSession s) {
        List<DrillCard> cards = cardRepository.findBySessionIdOrderBySeqNoAscIdAsc(s.getId());
        long waiting = cards.stream().filter(c -> DrillDict.CARD_WAITING.equals(c.getStatus())).count();
        long confirmed = cards.stream().filter(c -> DrillDict.CARD_CONFIRMED.equals(c.getStatus())).count();
        long timeout = cards.stream().filter(c -> DrillDict.CARD_TIMEOUT.equals(c.getStatus())).count();
        long skipped = cards.stream().filter(c -> DrillDict.CARD_SKIPPED.equals(c.getStatus())).count();
        long undisposed = cards.stream()
                .filter(c -> (DrillDict.CARD_TIMEOUT.equals(c.getStatus())
                        || DrillDict.CARD_SKIPPED.equals(c.getStatus()))
                        && (c.getDisposition() == null || c.getDisposition().isBlank()))
                .count();
        long postCount = postRepository.findBySessionIdOrderBySortNoAscIdAsc(s.getId()).size();
        Building building = s.getBuildingId() == null ? null
                : buildingRepository.findById(s.getBuildingId()).orElse(null);
        return new DrillViews.DrillListItem(
                s.getId(), s.getDrillNo(), s.getTitle(), s.getBuildingId(),
                building == null ? "" : building.getName(),
                s.getScenario(), s.getOrganizer(), s.getStatus(),
                DrillDict.sessionStatusName(s.getStatus()),
                postCount, cards.size(), waiting, confirmed, timeout, skipped, undisposed,
                s.getStartedAt(), s.getFinishedAt(), s.getCreatedAt());
    }

    // ==================== 校验 / 内部工具 ====================

    /** 就绪前的完整结构校验：岗位、卡、目标岗位、前置存在性与依赖环。 */
    private List<String> validateStructure(Long sessionId) {
        List<String> problems = new ArrayList<>();
        List<DrillPost> posts = postRepository.findBySessionIdOrderBySortNoAscIdAsc(sessionId);
        List<DrillCard> cards = cardRepository.findBySessionIdOrderBySeqNoAscIdAsc(sessionId);
        if (posts.isEmpty()) {
            problems.add("至少录入一个参与岗位");
        }
        if (cards.isEmpty()) {
            problems.add("至少编排一张现场指令卡");
        }
        Set<Long> postIds = new HashSet<>();
        posts.forEach(p -> postIds.add(p.getId()));
        Map<Long, List<Long>> prereqMap = new HashMap<>();
        Set<Integer> seqs = new HashSet<>();
        for (DrillCard c : cards) {
            if (!seqs.add(c.getSeqNo())) {
                problems.add("指令卡顺序号 " + c.getSeqNo() + " 重复");
            }
            if (c.getTargetPostId() == null || !postIds.contains(c.getTargetPostId())) {
                problems.add("第 " + c.getSeqNo() + " 张卡缺少有效目标岗位");
            }
            if (c.getTimeLimitSeconds() < 1 || c.getTimeLimitSeconds() > 3600) {
                problems.add("第 " + c.getSeqNo() + " 张卡响应时限超出 1~3600 秒");
            }
        }
        Set<Long> cardIds = new HashSet<>();
        cards.forEach(c -> cardIds.add(c.getId()));
        for (DrillCardDependency d : dependencyRepository.findBySessionIdOrderByIdAsc(sessionId)) {
            if (!cardIds.contains(d.getCardId()) || !cardIds.contains(d.getPrereqId())) {
                problems.add("存在指向已删除指令卡的前置关系");
                continue;
            }
            if (d.getCardId().equals(d.getPrereqId())) {
                problems.add("指令卡不能把自己设为前置节点");
            }
            prereqMap.computeIfAbsent(d.getCardId(), k -> new ArrayList<>()).add(d.getPrereqId());
        }
        if (hasCycle(cards, prereqMap)) {
            problems.add("指令卡前置关系存在循环依赖，请解除环后再就绪");
        }
        return problems;
    }

    private boolean hasCycle(List<DrillCard> cards, Map<Long, List<Long>> prereqMap) {
        // 0=未访问 1=访问中 2=已完成，DFS 遇到访问中节点即有环
        Map<Long, Integer> mark = new HashMap<>();
        for (DrillCard c : cards) {
            if (cycleDfs(c.getId(), prereqMap, mark)) {
                return true;
            }
        }
        return false;
    }

    private boolean cycleDfs(Long node, Map<Long, List<Long>> prereqMap, Map<Long, Integer> mark) {
        int state = mark.getOrDefault(node, 0);
        if (state == 1) {
            return true;
        }
        if (state == 2) {
            return false;
        }
        mark.put(node, 1);
        for (Long prereq : prereqMap.getOrDefault(node, List.of())) {
            if (cycleDfs(prereq, prereqMap, mark)) {
                return true;
            }
        }
        mark.put(node, 2);
        return false;
    }

    /** 某张卡当前的阻塞来源（前置未确认项）。 */
    private List<DrillViews.BlockerView> blockersOf(Long sessionId, Long cardId) {
        List<DrillCard> all = cardRepository.findBySessionIdOrderBySeqNoAscIdAsc(sessionId);
        Map<Long, DrillCard> byId = new HashMap<>();
        all.forEach(c -> byId.put(c.getId(), c));
        List<DrillViews.BlockerView> blockers = new ArrayList<>();
        for (DrillCardDependency d : dependencyRepository.findByCardIdOrderByIdAsc(cardId)) {
            DrillCard pre = byId.get(d.getPrereqId());
            if (pre != null && !DrillDict.CARD_CONFIRMED.equals(pre.getStatus())) {
                String reason = "前置 #" + pre.getSeqNo() + "「" + pre.getTitle() + "」当前为「"
                        + DrillDict.cardStatusName(pre.getStatus()) + "」，尚未确认";
                blockers.add(new DrillViews.BlockerView(
                        pre.getId(), pre.getSeqNo(), pre.getTitle(), pre.getTargetPostName(),
                        pre.getStatus(), DrillDict.cardStatusName(pre.getStatus()), reason));
            }
        }
        return blockers;
    }

    /** 依赖某前置卡、且尚未到终态的卡（用于超时 / 跳过时点名阻塞范围）。 */
    private List<DrillCard> dependentPendingCards(Long sessionId, Long prereqId) {
        Set<Long> ids = new HashSet<>();
        collectDependents(prereqId, ids);
        if (ids.isEmpty()) {
            return List.of();
        }
        return cardRepository.findBySessionIdOrderBySeqNoAscIdAsc(sessionId).stream()
                .filter(c -> ids.contains(c.getId()) && !DrillDict.isCardTerminal(c.getStatus()))
                .toList();
    }

    private void collectDependents(Long prereqId, Set<Long> acc) {
        for (DrillCardDependency d : dependencyRepository.findByPrereqIdOrderByIdAsc(prereqId)) {
            if (acc.add(d.getCardId())) {
                collectDependents(d.getCardId(), acc);
            }
        }
    }

    /** 结束演练前的缺项清单：非终态卡 + 超时/跳过未处置。 */
    private List<String> finishBlockers(List<DrillCard> cards) {
        List<String> blockers = new ArrayList<>();
        for (DrillCard c : cards) {
            switch (c.getStatus()) {
                case DrillDict.CARD_PENDING -> blockers.add(
                        "#" + c.getSeqNo() + "「" + c.getTitle() + "」仍为待下达（需下达并确认，或填写原因跳过）");
                case DrillDict.CARD_WAITING -> blockers.add(
                        "#" + c.getSeqNo() + "「" + c.getTitle() + "」仍在等待响应（需确认或跳过）");
                case DrillDict.CARD_TIMEOUT, DrillDict.CARD_SKIPPED -> {
                    if (c.getDisposition() == null || c.getDisposition().isBlank()) {
                        blockers.add("#" + c.getSeqNo() + "「" + c.getTitle() + "」已"
                                + DrillDict.cardStatusName(c.getStatus()) + "，但尚未填写处置结论");
                    }
                }
                default -> {
                    // CONFIRMED 无缺项
                }
            }
        }
        return blockers;
    }

    private DrillSession ensureSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new BizException("演练场次不存在：" + id));
    }

    private DrillCard ensureCard(Long sessionId, Long cardId) {
        DrillCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new BizException("指令卡不存在：" + cardId));
        if (!sessionId.equals(card.getSessionId())) {
            throw new BizException("指令卡不属于该演练场次");
        }
        return card;
    }

    /**
     * 卡片动作通用幂等回放：同一 action_key 的事件已存在则直接回放当前详情。
     * 放在所有卡片状态校验之前，保证「已确认后重发同一确认」也不会报错。
     */
    private DrillViews.DrillDetailView replayIfKeyExists(DrillSession session, String actionKey) {
        if (actionKey != null
                && eventRepository.findBySessionIdAndActionKey(session.getId(), actionKey).isPresent()) {
            return buildDetail(session, LocalDateTime.now());
        }
        return null;
    }

    /**
     * 场次动作幂等回放：同一 action_key 的事件已存在（网络重发 / 双击 / 多页面交替），
     * 直接返回当前详情，不报错、不新增事件、不二次推进。
     * 动作标识不存在却又不满足前置状态时，按正常业务规则拦截。
     */
    private DrillViews.DrillDetailView replayIfSame(DrillSession session, String actionKey, String action) {
        DrillViews.DrillDetailView replay = replayIfKeyExists(session, actionKey);
        if (replay != null) {
            return replay;
        }
        throw new BizException("演练当前为「" + DrillDict.sessionStatusName(session.getStatus())
                + "」，不能执行「" + DrillDict.actionName(action) + "」");
    }

    /** 追加一条事件。调用方均已持有场次行锁；action_key 唯一约束兜底并发重复提交。 */
    private void writeEvent(Long sessionId, String action, String targetType,
                            Long targetId, String actionKey, String from, String to,
                            String note, String operator, LocalDateTime at) {
        if (actionKey != null && eventRepository
                .findBySessionIdAndActionKey(sessionId, actionKey).isPresent()) {
            // 正常流程在动作前已回放；兜底拦住并发下的第二条
            throw new ConflictException("该动作已处理，请勿重复提交");
        }
        DrillEvent event = new DrillEvent();
        event.setSessionId(sessionId);
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setActionKey(actionKey);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setOperator(operator);
        event.setNote(note);
        event.setCreatedAt(at);
        try {
            eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // uk_event_action_key 兜底：并发重复动作只有一条事件落库
            throw new ConflictException("该动作已处理（动作标识重复），请勿重复提交");
        }
    }

    private LocalDateTime nowSec() {
        return nowSec(LocalDateTime.now());
    }

    private LocalDateTime nowSec(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.SECONDS);
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
