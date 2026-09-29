package com.fire.protect.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fire.protect.common.BizException;
import com.fire.protect.domain.DrillScript;
import com.fire.protect.domain.DrillScriptCard;
import com.fire.protect.dto.DrillDtos;
import com.fire.protect.dto.DrillViews;
import com.fire.protect.repository.DrillScriptCardRepository;
import com.fire.protect.repository.DrillScriptRepository;
import com.fire.protect.security.CurrentUserHolder;

/**
 * 常用演练脚本维护。
 *
 * <p>脚本与场次是「复制」关系、不是引用：应用脚本时把岗位与指令卡复制一份到场次，
 * 之后脚本再怎么改，已经就绪 / 开始的场次都不受影响（冻结要求由脚本独立性天然保证）。
 */
@Service
public class DrillScriptService {

    private final DrillScriptRepository scriptRepository;
    private final DrillScriptCardRepository cardRepository;

    public DrillScriptService(DrillScriptRepository scriptRepository,
                              DrillScriptCardRepository cardRepository) {
        this.scriptRepository = scriptRepository;
        this.cardRepository = cardRepository;
    }

    @Transactional(readOnly = true)
    public List<DrillViews.ScriptView> list() {
        return scriptRepository.findAllByOrderByIdDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public DrillViews.ScriptView detail(Long id) {
        return toView(ensureScript(id));
    }

    @Transactional
    public DrillViews.ScriptView create(DrillDtos.SaveScriptReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        validateReq(req, null);
        LocalDateTime now = LocalDateTime.now();
        DrillScript script = new DrillScript();
        script.setName(req.getName().trim());
        script.setScenario(req.getScenario());
        script.setCreatedBy(CurrentUserHolder.require().getDisplayName());
        script.setCreatedAt(now);
        script.setUpdatedAt(now);
        DrillScript saved = scriptRepository.save(script);
        saveCards(saved.getId(), req.getCards());
        return detail(saved.getId());
    }

    @Transactional
    public DrillViews.ScriptView update(Long id, DrillDtos.SaveScriptReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        DrillScript script = ensureScript(id);
        validateReq(req, id);
        script.setName(req.getName().trim());
        script.setScenario(req.getScenario());
        script.setUpdatedAt(LocalDateTime.now());
        scriptRepository.save(script);
        cardRepository.deleteByScriptId(id);
        cardRepository.flush();
        saveCards(id, req.getCards());
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "演练组织者");
        DrillScript script = ensureScript(id);
        cardRepository.deleteByScriptId(id);
        scriptRepository.delete(script);
    }

    // ==================== 内部工具 ====================

    private void validateReq(DrillDtos.SaveScriptReq req, Long selfId) {
        if (req == null) {
            throw new BizException("请求体为空");
        }
        String name = req.getName() == null ? null : req.getName().trim();
        if (name == null || name.isBlank()) {
            throw new BizException("脚本名称不能为空");
        }
        for (DrillScript s : scriptRepository.findAll()) {
            if (name.equals(s.getName()) && (selfId == null || !s.getId().equals(selfId))) {
                throw new BizException("已存在同名脚本：" + name);
            }
        }
        if (req.getCards() == null || req.getCards().isEmpty()) {
            throw new BizException("脚本至少要编排一张指令卡");
        }
        validateCards(req.getCards());
    }

    /** 卡顺序号唯一、前置只能引用本脚本已存在的顺序号、时限为正 —— 编排期就把链校验干净。 */
    static void validateCards(List<DrillDtos.ScriptCardReq> cards) {
        Map<Integer, DrillDtos.ScriptCardReq> bySeq = new LinkedHashMap<>();
        for (DrillDtos.ScriptCardReq c : cards) {
            if (c.getSeqNo() == null || c.getSeqNo() <= 0) {
                throw new BizException("指令卡顺序号必须为正整数");
            }
            if (bySeq.put(c.getSeqNo(), c) != null) {
                throw new BizException("指令卡顺序号重复：" + c.getSeqNo());
            }
            if (c.getTitle() == null || c.getTitle().isBlank()) {
                throw new BizException("第 " + c.getSeqNo() + " 张卡标题不能为空");
            }
            if (c.getTargetPosCode() == null || c.getTargetPosCode().isBlank()) {
                throw new BizException("第 " + c.getSeqNo() + " 张卡必须选择目标岗位");
            }
            if (c.getTimeLimitSec() == null || c.getTimeLimitSec() <= 0) {
                throw new BizException("第 " + c.getSeqNo() + " 张卡响应时限必须为正整数秒");
            }
        }
        for (DrillDtos.ScriptCardReq c : cards) {
            if (c.getPrereqSeqs() != null) {
                for (Integer prereq : c.getPrereqSeqs()) {
                    if (prereq.equals(c.getSeqNo())) {
                        throw new BizException("第 " + c.getSeqNo() + " 张卡不能把自己设为前置节点");
                    }
                    if (!bySeq.containsKey(prereq)) {
                        throw new BizException("第 " + c.getSeqNo() + " 张卡引用了不存在的前置顺序号：" + prereq);
                    }
                }
            }
        }
    }

    private void saveCards(Long scriptId, List<DrillDtos.ScriptCardReq> cards) {
        for (DrillDtos.ScriptCardReq c : cards.stream()
                .sorted((a, b) -> Integer.compare(a.getSeqNo(), b.getSeqNo())).toList()) {
            DrillScriptCard entity = new DrillScriptCard();
            entity.setScriptId(scriptId);
            entity.setSeqNo(c.getSeqNo());
            entity.setTitle(c.getTitle().trim());
            entity.setTargetPosCode(c.getTargetPosCode().trim());
            entity.setTimeLimitSec(c.getTimeLimitSec());
            entity.setOnSiteHint(c.getOnSiteHint());
            entity.setPrereqSeqs(joinInts(c.getPrereqSeqs()));
            cardRepository.save(entity);
        }
    }

    static String joinInts(List<Integer> seqs) {
        if (seqs == null || seqs.isEmpty()) {
            return null;
        }
        return seqs.stream().distinct().map(String::valueOf)
                .sorted((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)))
                .reduce((a, b) -> a + "," + b).orElse(null);
    }

    static List<Integer> splitInts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (String part : Arrays.asList(raw.split(","))) {
            String p = part.trim();
            if (!p.isEmpty()) {
                result.add(Integer.parseInt(p));
            }
        }
        return result;
    }

    DrillScript ensureScript(Long id) {
        return scriptRepository.findById(id)
                .orElseThrow(() -> new BizException("演练脚本不存在：" + id));
    }

    private DrillViews.ScriptView toView(DrillScript script) {
        List<DrillScriptCard> rows = cardRepository.findByScriptIdOrderBySeqNoAscIdAsc(script.getId());
        List<DrillViews.ScriptCardView> cards = new ArrayList<>();
        for (DrillScriptCard c : rows) {
            cards.add(new DrillViews.ScriptCardView(c.getId(), c.getSeqNo(), c.getTitle(),
                    c.getTargetPosCode(), c.getTimeLimitSec(), c.getOnSiteHint(), splitInts(c.getPrereqSeqs())));
        }
        return new DrillViews.ScriptView(script.getId(), script.getName(), script.getScenario(),
                script.getCreatedBy(), script.getCreatedAt(), script.getUpdatedAt(), cards, cards.size());
    }
}
