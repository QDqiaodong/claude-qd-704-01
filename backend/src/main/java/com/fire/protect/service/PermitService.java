package com.fire.protect.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fire.protect.common.BizException;
import com.fire.protect.common.ConflictException;
import com.fire.protect.common.ForbiddenException;
import com.fire.protect.domain.Building;
import com.fire.protect.domain.BuildingFloor;
import com.fire.protect.domain.DisableMeasure;
import com.fire.protect.domain.DisablePermit;
import com.fire.protect.domain.DisablePermitPoint;
import com.fire.protect.domain.FacilityPoint;
import com.fire.protect.domain.FireDict;
import com.fire.protect.domain.MaintenanceOrder;
import com.fire.protect.domain.PermitActionLog;
import com.fire.protect.domain.PermitOrderWaiver;
import com.fire.protect.domain.ResetVerification;
import com.fire.protect.domain.SysUser;
import com.fire.protect.dto.PermitDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.repository.BuildingFloorRepository;
import com.fire.protect.repository.BuildingRepository;
import com.fire.protect.repository.DisableMeasureRepository;
import com.fire.protect.repository.DisablePermitPointRepository;
import com.fire.protect.repository.DisablePermitRepository;
import com.fire.protect.repository.FacilityPointRepository;
import com.fire.protect.repository.MaintenanceOrderRepository;
import com.fire.protect.repository.PermitActionLogRepository;
import com.fire.protect.repository.PermitOrderWaiverRepository;
import com.fire.protect.repository.ResetVerificationRepository;
import com.fire.protect.repository.SysUserRepository;
import com.fire.protect.security.CurrentUserHolder;

/**
 * 停用许可与复位闭环。
 *
 * <p>状态机：DRAFT → PENDING_APPROVAL → EFFECTIVE → PENDING_RESET → CLOSED；
 * 拒绝回 DRAFT（范围锁定，只能改表头与措施），撤销是仅止于 DRAFT / PENDING_APPROVAL 的旁支。
 *
 * <p>并发：主表带 {@code @Version}，每个动作请求都要带详情里看到的版本号；
 * 同一版本只有一个动作成功，失败方拿到 409 且不写任何留痕。批准挂标记 / 关闭解标记
 * 都与状态翻转在同一事务里完成，不会出现「许可关闭但部分点位仍显示停用」。
 */
@Service
public class PermitService {

    /** 值班口径：计划结束日剩余 ≤ N 天算「即将到期」 */
    private static final int EXPIRE_SOON_DAYS = 3;

    private static final List<String> UNFINISHED_ORDER_STATUS = List.of("PENDING", "ASSIGNED", "WORKING");
    private static final List<String> ACTIVE_STATUSES = List.of("EFFECTIVE", "PENDING_RESET");

    private final DisablePermitRepository permitRepository;
    private final DisablePermitPointRepository permitPointRepository;
    private final DisableMeasureRepository measureRepository;
    private final ResetVerificationRepository verificationRepository;
    private final PermitActionLogRepository logRepository;
    private final PermitOrderWaiverRepository waiverRepository;
    private final FacilityPointRepository pointRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final BuildingRepository buildingRepository;
    private final BuildingFloorRepository floorRepository;
    private final SysUserRepository userRepository;

    public PermitService(DisablePermitRepository permitRepository,
                         DisablePermitPointRepository permitPointRepository,
                         DisableMeasureRepository measureRepository,
                         ResetVerificationRepository verificationRepository,
                         PermitActionLogRepository logRepository,
                         PermitOrderWaiverRepository waiverRepository,
                         FacilityPointRepository pointRepository,
                         MaintenanceOrderRepository orderRepository,
                         BuildingRepository buildingRepository,
                         BuildingFloorRepository floorRepository,
                         SysUserRepository userRepository) {
        this.permitRepository = permitRepository;
        this.permitPointRepository = permitPointRepository;
        this.measureRepository = measureRepository;
        this.verificationRepository = verificationRepository;
        this.logRepository = logRepository;
        this.waiverRepository = waiverRepository;
        this.pointRepository = pointRepository;
        this.orderRepository = orderRepository;
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.userRepository = userRepository;
    }

    // ==================== 用户 ====================

    @Transactional(readOnly = true)
    public List<Views.UserView> users() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(u -> new Views.UserView(u.getId(), u.getUserName(), u.getDisplayName(),
                        u.getRole(), FireDict.roleName(u.getRole())))
                .toList();
    }

    // ==================== 列表 / 值班页 ====================

    @Transactional(readOnly = true)
    public List<Views.PermitListView> list(Long buildingId, String status) {
        List<DisablePermit> permits;
        if (buildingId != null && status != null && !status.isBlank()) {
            permits = permitRepository.findAllByOrderByIdDesc().stream()
                    .filter(p -> buildingId.equals(p.getBuildingId()) && status.equals(p.getStatus())).toList();
        } else if (buildingId != null) {
            permits = permitRepository.findByBuildingIdOrderByIdDesc(buildingId);
        } else if (status != null && !status.isBlank()) {
            permits = permitRepository.findByStatusOrderByIdDesc(status);
        } else {
            permits = permitRepository.findAllByOrderByIdDesc();
        }
        return permits.stream().map(this::toListView).toList();
    }

    /**
     * 值班页：当前生效 / 即将到期（≤3 天）/ 已超期未复位 三栏 + 待复位待办。
     * 全部按当天日期实时计算，刷新页面或服务重启口径一致；系统从不自动关闭超期许可。
     */
    @Transactional(readOnly = true)
    public Views.DutyBoardDTO dutyBoard() {
        LocalDate today = LocalDate.now();
        List<Views.PermitListView> effective = new ArrayList<>();
        List<Views.PermitListView> expiringSoon = new ArrayList<>();
        List<Views.PermitListView> expired = new ArrayList<>();
        List<Views.PermitListView> pendingReset = new ArrayList<>();
        for (DisablePermit permit : permitRepository.findByStatusInOrderByIdDesc(ACTIVE_STATUSES)) {
            Views.PermitListView view = toListView(permit);
            if ("PENDING_RESET".equals(permit.getStatus())) {
                pendingReset.add(view);
                if (view.expired()) {
                    expired.add(view);
                }
                continue;
            }
            // EFFECTIVE
            if (view.expired()) {
                expired.add(view);
            } else if (view.expiringSoon()) {
                expiringSoon.add(view);
            } else {
                effective.add(view);
            }
        }
        return new Views.DutyBoardDTO(today, effective, expiringSoon, expired, pendingReset,
                expired.size() + pendingReset.size());
    }

    // ==================== 详情 ====================

    @Transactional(readOnly = true)
    public Views.PermitDetailView detail(Long id) {
        DisablePermit permit = ensurePermit(id);
        Building building = buildingRepository.findById(permit.getBuildingId()).orElse(null);

        // 最新一轮批次（取最大批次序号）
        List<ResetVerification> verifications = verificationRepository.findByPermitIdOrderByIdAsc(id);
        int lastRound = verifications.stream()
                .mapToInt(v -> batchRound(v.getBatchNo())).max().orElse(0);
        String lastBatch = lastRound > 0 ? batchNo(id, lastRound) : null;
        Map<Long, ResetVerification> latestByPoint = new HashMap<>();
        for (ResetVerification v : verifications) {
            if (v.getBatchNo().equals(lastBatch)) {
                latestByPoint.put(v.getPointId(), v);
            }
        }

        List<Views.PermitPointView> pointViews = new ArrayList<>();
        for (DisablePermitPoint pp : permitPointRepository.findByPermitIdOrderByIdAsc(id)) {
            FacilityPoint point = pointRepository.findById(pp.getPointId()).orElse(null);
            BuildingFloor floor = point == null || point.getFloorId() == null ? null
                    : floorRepository.findById(point.getFloorId()).orElse(null);
            ResetVerification latest = latestByPoint.get(pp.getPointId());
            pointViews.add(new Views.PermitPointView(
                    pp.getId(), pp.getPointId(),
                    point == null ? "" : point.getPointCode(),
                    point == null ? null : point.getFloorId(),
                    floor == null ? "" : floor.getFloorCode(),
                    point == null ? null : point.getFacilityType(),
                    point == null ? "" : FireDict.facilityTypeName(point.getFacilityType()),
                    point == null ? "" : point.getLocationDesc(),
                    pp.getOriginStatus(), FireDict.pointStatusName(pp.getOriginStatus()),
                    pp.isMarked(),
                    latest == null ? null : latest.getResult(),
                    latest == null ? "" : ("PASS".equals(latest.getResult()) ? "合格" : "不合格"),
                    latest == null ? null : latest.getFailReason(),
                    latest == null ? null : latest.getVerifyDesc()));
        }

        List<Views.MeasureView> measureViews = measureRepository.findByPermitIdOrderByIdAsc(id).stream()
                .map(m -> new Views.MeasureView(
                        m.getId(), m.getMeasureType(), FireDict.measureTypeName(m.getMeasureType()),
                        m.getContent(), m.getSpecDesc(), m.getCreatedBy(), m.getCreatedAt(),
                        m.getConfirmedAt(), m.getConfirmedBy(), m.getConfirmRemark(),
                        m.getConfirmedAt() != null))
                .toList();

        return new Views.PermitDetailView(
                permit.getId(), permit.getPermitNo(), permit.getBuildingId(),
                building == null ? "" : building.getName(),
                permit.getStatus(), FireDict.permitStatusName(permit.getStatus()),
                permit.getReason(), permit.getPlanStart(), permit.getPlanEnd(),
                permit.getSiteOwnerId(), permit.getSiteOwnerName(), permit.getCreatedBy(),
                permit.isScopeLocked(),
                permit.getApprovedBy(), permit.getApprovedAt(),
                permit.getRejectReason(),
                permit.getClosedBy(), permit.getClosedAt(),
                permit.getRevokedBy(), permit.getRevokedAt(), permit.getRevokeReason(),
                permit.getResetStartedAt(),
                permit.getLastVerifyAt(), permit.getLastVerifyBy(), permit.getLastVerifyFailReason(),
                isExpired(permit), isExpiringSoon(permit),
                permit.getVersion(), permit.getCreatedAt(),
                pointViews, measureViews, orderViews(id), logViews(id));
    }

    // ==================== 草稿：创建 / 编辑 ====================

    @Transactional
    public Views.PermitDetailView create(PermitDtos.SaveReq req) {
        SysUser user = CurrentUserHolder.require();
        CurrentUserHolder.requireRole("FIRE_MANAGER", "消防主管");

        ValidatedScope scope = validateScope(req, null);
        LocalDateTime now = LocalDateTime.now();
        DisablePermit permit = new DisablePermit();
        permit.setPermitNo(nextPermitNo());
        permit.setBuildingId(scope.buildingId);
        permit.setReason(scope.reason);
        permit.setPlanStart(req.getPlanStart());
        permit.setPlanEnd(req.getPlanEnd());
        permit.setSiteOwnerId(scope.siteOwner.getId());
        permit.setSiteOwnerName(scope.siteOwner.getDisplayName());
        permit.setCreatedBy(user.getDisplayName());
        permit.setStatus("DRAFT");
        permit.setScopeLocked(false);
        permit.setCreatedAt(now);
        permit.setUpdatedAt(now);
        DisablePermit saved = permitRepository.save(permit);
        savePoints(saved.getId(), scope.pointIds, now);
        saveMeasures(saved.getId(), req.getMeasures(), user, now);
        writeLog(saved.getId(), "CREATE", null, "DRAFT", "创建停用许可草稿");
        return detail(saved.getId());
    }

    /**
     * 编辑草稿：首次提交过（被拒绝）的许可范围已锁 —— 点位清单不允许再变，
     * 只能改停用原因、起止时间、现场负责人与补偿措施。
     */
    @Transactional
    public Views.PermitDetailView update(Long id, PermitDtos.SaveReq req) {
        SysUser user = CurrentUserHolder.require();
        CurrentUserHolder.requireRole("FIRE_MANAGER", "消防主管");
        DisablePermit permit = ensurePermit(id);
        if (!"DRAFT".equals(permit.getStatus())) {
            throw new BizException("只有草稿状态的许可可以编辑，当前状态：" + FireDict.permitStatusName(permit.getStatus()));
        }
        ValidatedScope scope = validateScope(req, permit);

        List<Long> existing = permitPointRepository.findByPermitIdOrderByIdAsc(id).stream()
                .map(DisablePermitPoint::getPointId).sorted().toList();
        List<Long> requested = scope.pointIds.stream().sorted().toList();
        if (permit.isScopeLocked() && !existing.equals(requested)) {
            throw new BizException("许可提交后受影响范围已锁定，不能再静默增删点位；请新建许可或按原范围重新提交");
        }

        permit.setBuildingId(scope.buildingId);
        permit.setReason(scope.reason);
        permit.setPlanStart(req.getPlanStart());
        permit.setPlanEnd(req.getPlanEnd());
        permit.setSiteOwnerId(scope.siteOwner.getId());
        permit.setSiteOwnerName(scope.siteOwner.getDisplayName());
        permit.setUpdatedAt(LocalDateTime.now());
        permitRepository.save(permit);

        if (!permit.isScopeLocked()) {
            permitPointRepository.deleteByPermitId(id);
            permitPointRepository.flush();
            savePoints(id, scope.pointIds, LocalDateTime.now());
        }
        // 措施在批准前允许反复调整（措施不是受影响范围）
        measureRepository.deleteByPermitId(id);
        measureRepository.flush();
        saveMeasures(id, req.getMeasures(), user, LocalDateTime.now());
        writeLog(id, "UPDATE", "DRAFT", "DRAFT",
                permit.isScopeLocked() ? "被拒绝后修改表头与补偿措施（范围保持锁定）" : "编辑草稿");
        return detail(id);
    }

    // ==================== 提交 / 批准 / 拒绝 / 撤销 ====================

    @Transactional
    public Views.PermitDetailView submit(Long id) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "消防主管");
        DisablePermit permit = loadAndCheckVersion(id, null);
        if (!"DRAFT".equals(permit.getStatus())) {
            throw new BizException("只有草稿可以提交批准，当前状态：" + FireDict.permitStatusName(permit.getStatus()));
        }
        List<Long> pointIds = permitPointRepository.findByPermitIdOrderByIdAsc(id).stream()
                .map(DisablePermitPoint::getPointId).toList();
        if (pointIds.isEmpty()) {
            throw new BizException("受影响范围为空，请至少选择一个设施点位");
        }
        long measureCount = measureRepository.countByPermitId(id);
        if (measureCount == 0) {
            throw new BizException("请逐项登记补偿措施（替代灭火器 / 加密巡查 / 临时警戒等）后再提交");
        }
        validateWindow(permit);

        permit.setScopeLocked(true);
        permit.setStatus("PENDING_APPROVAL");
        permit.setUpdatedAt(LocalDateTime.now());
        saveWithVersionGuard(permit);
        writeLog(id, "SUBMIT", "DRAFT", "PENDING_APPROVAL", "提交安全负责人批准，范围 " + pointIds.size() + " 个点位");
        return detail(id);
    }

    @Transactional
    public Views.PermitDetailView approve(Long id) {
        CurrentUserHolder.requireRole("SAFETY_OFFICER", "安全负责人");
        DisablePermit permit = loadAndCheckVersion(id, null);
        if (!"PENDING_APPROVAL".equals(permit.getStatus())) {
            throw new BizException("只有待批准的许可可以批准，当前状态：" + FireDict.permitStatusName(permit.getStatus()));
        }
        validateWindow(permit);

        List<DisablePermitPoint> rows = permitPointRepository.findByPermitIdOrderByIdAsc(id);
        List<Long> pointIds = rows.stream().map(DisablePermitPoint::getPointId).toList();
        ensureNoConcurrentActivePermit(permit, pointIds);

        // 快照原状态（许可停用绝不覆盖原状态）
        Map<Long, FacilityPoint> pointMap = pointRepository.findByIdIn(pointIds).stream()
                .collect(Collectors.toMap(FacilityPoint::getId, p -> p));
        LocalDateTime now = LocalDateTime.now();
        for (DisablePermitPoint row : rows) {
            FacilityPoint point = pointMap.get(row.getPointId());
            if (point == null) {
                throw new BizException("范围内点位已不存在：" + row.getPointId());
            }
            if (point.getActiveDisablePermitId() != null) {
                throw new BizException("点位 " + point.getPointCode() + " 已被另一张生效许可占用，不能重复批准");
            }
            row.setOriginStatus(point.getStatus());
            row.setMarked(true);
            permitPointRepository.save(row);
        }

        // 先挂叠加标记（原生 UPDATE，绕过设施生命周期回调，不动原状态与检查日）
        permitRepository.markPoints(id, pointIds);
        permitRepository.updatePointMarked(id, true);

        permit.setStatus("EFFECTIVE");
        permit.setApprovedAt(now);
        permit.setApprovedBy(CurrentUserHolder.require().getDisplayName());
        permit.setRejectReason(null);
        permit.setUpdatedAt(now);
        saveWithVersionGuard(permit);
        writeLog(id, "APPROVE", "PENDING_APPROVAL", "EFFECTIVE",
                "批准生效，" + pointIds.size() + " 个点位挂「许可停用」标记，原状态保留");
        return detail(id);
    }

    @Transactional
    public Views.PermitDetailView reject(Long id, String reason) {
        CurrentUserHolder.requireRole("SAFETY_OFFICER", "安全负责人");
        DisablePermit permit = loadAndCheckVersion(id, null);
        if (!"PENDING_APPROVAL".equals(permit.getStatus())) {
            throw new BizException("只有待批准的许可可以拒绝，当前状态：" + FireDict.permitStatusName(permit.getStatus()));
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException("拒绝时必须填写意见");
        }
        permit.setStatus("DRAFT");
        permit.setScopeLocked(true);
        permit.setRejectReason(reason);
        permit.setUpdatedAt(LocalDateTime.now());
        saveWithVersionGuard(permit);
        writeLog(id, "REJECT", "PENDING_APPROVAL", "DRAFT", "拒绝退回：" + reason);
        return detail(id);
    }

    @Transactional
    public Views.PermitDetailView revoke(Long id, String reason) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "消防主管");
        DisablePermit permit = loadAndCheckVersion(id, null);
        if (!"DRAFT".equals(permit.getStatus()) && !"PENDING_APPROVAL".equals(permit.getStatus())) {
            throw new BizException("只有草稿或待批准的许可可以撤销；生效后的许可必须走复位关闭流程");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException("撤销必须填写原因");
        }
        String fromStatus = permit.getStatus();
        LocalDateTime now = LocalDateTime.now();
        permit.setStatus("REVOKED");
        permit.setRevokedAt(now);
        permit.setRevokedBy(CurrentUserHolder.require().getDisplayName());
        permit.setRevokeReason(reason);
        permit.setUpdatedAt(now);
        saveWithVersionGuard(permit);
        writeLog(id, "REVOKE", fromStatus, "REVOKED", "撤销许可：" + reason);
        return detail(id);
    }

    // ==================== 补偿措施确认 / 维保单处理 ====================

    @Transactional
    public Views.PermitDetailView confirmMeasure(Long measureId, String remark) {
        DisableMeasure measure = measureRepository.findById(measureId)
                .orElseThrow(() -> new BizException("补偿措施不存在：" + measureId));
        SysUser user = requireSiteOwnerOf(measure.getPermitId());
        DisablePermit permit = ensurePermit(measure.getPermitId());
        if (!ACTIVE_STATUSES.contains(permit.getStatus())) {
            throw new BizException("补偿措施只能在许可生效后确认，当前状态：" + FireDict.permitStatusName(permit.getStatus()));
        }
        if (measure.getConfirmedAt() != null) {
            throw new BizException("该措施已确认收口，请勿重复提交");
        }
        measure.setConfirmedAt(LocalDateTime.now());
        measure.setConfirmedBy(user.getDisplayName());
        measure.setConfirmRemark(remark);
        measureRepository.save(measure);
        writeLog(permit.getId(), "MEASURE_DONE", permit.getStatus(), permit.getStatus(),
                "确认补偿措施已收口：" + FireDict.measureTypeName(measure.getMeasureType()) + " " + measure.getContent());
        return detail(permit.getId());
    }

    @Transactional
    public Views.PermitDetailView waiveOrder(Long permitId, Long orderId, String note) {
        SysUser user = requireSiteOwnerOf(permitId);
        DisablePermit permit = ensurePermit(permitId);
        if (!ACTIVE_STATUSES.contains(permit.getStatus())) {
            throw new BizException("当前状态不能登记维保处理说明：" + FireDict.permitStatusName(permit.getStatus()));
        }
        MaintenanceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException("维保单不存在：" + orderId));
        Set<Long> pointIds = permitPointRepository.findByPermitIdOrderByIdAsc(permitId).stream()
                .map(DisablePermitPoint::getPointId).collect(Collectors.toSet());
        if (order.getPointId() == null || !pointIds.contains(order.getPointId())) {
            throw new BizException("该维保单关联的点位不在本许可范围内");
        }
        if (!UNFINISHED_ORDER_STATUS.contains(order.getStatus())) {
            throw new BizException("该维保单已是「" + FireDict.orderStatusName(order.getStatus()) + "」，无需再登记说明");
        }
        if (note == null || note.isBlank()) {
            throw new BizException("必须填写处理说明");
        }
        PermitOrderWaiver waiver = waiverRepository.findByPermitIdAndOrderId(permitId, orderId)
                .orElseGet(() -> {
                    PermitOrderWaiver w = new PermitOrderWaiver();
                    w.setPermitId(permitId);
                    w.setOrderId(orderId);
                    return w;
                });
        waiver.setHandleNote(note);
        waiver.setCreatedBy(user.getDisplayName());
        waiver.setCreatedAt(LocalDateTime.now());
        waiverRepository.save(waiver);
        writeLog(permitId, "ORDER_WAIVER", permit.getStatus(), permit.getStatus(),
                "关联维保单 " + order.getOrderNo() + " 处理说明：" + note);
        return detail(permitId);
    }

    // ==================== 复位核验 / 关闭 ====================

    /**
     * 发起 / 重新发起复位核验。前置：所有补偿措施已确认收口、关联未完成维保单已处理；
     * 必须逐个点位给结论。任一点位不合格 → 整许可保持待复位并记录失败原因，修正后可再次核验。
     */
    @Transactional
    public Views.PermitDetailView resetVerify(Long id, PermitDtos.ResetVerifyReq req) {
        SysUser user = requireSiteOwnerOf(id);
        DisablePermit permit = loadAndCheckVersion(id, req == null ? null : req.getVersion());
        String status = permit.getStatus();
        if (!ACTIVE_STATUSES.contains(status)) {
            throw new BizException("只有生效或待复位的许可可以发起复位核验，当前状态："
                    + FireDict.permitStatusName(status));
        }

        // 前置 1：补偿措施全部收口
        long unconfirmed = measureRepository.countByPermitIdAndConfirmedAtIsNull(id);
        if (unconfirmed > 0) {
            throw new BizException("还有 " + unconfirmed + " 项补偿措施未确认收口，不能发起复位核验");
        }

        // 前置 2：关联未完成维保单全部处理（推进完成或逐单登记说明）
        List<Views.PermitOrderView> orderViews = orderViews(id);
        List<Views.PermitOrderView> blocking = orderViews.stream()
                .filter(v -> !v.handled()).toList();
        if (!blocking.isEmpty()) {
            String nos = blocking.stream().map(Views.PermitOrderView::orderNo).collect(Collectors.joining("、"));
            throw new BizException("关联维保单尚未处理完成：" + nos + "；可先推进工单或逐单登记处理说明");
        }

        // 逐点位核验结论
        List<DisablePermitPoint> rows = permitPointRepository.findByPermitIdOrderByIdAsc(id);
        Map<Long, PermitDtos.ResetItemReq> itemMap = new LinkedHashMap<>();
        if (req != null && req.getItems() != null) {
            for (PermitDtos.ResetItemReq item : req.getItems()) {
                if (item.getPointId() == null || itemMap.put(item.getPointId(), item) != null) {
                    throw new BizException("复位核验明细存在空点位或重复点位");
                }
            }
        }
        if (itemMap.size() != rows.size()) {
            throw new BizException("必须对全部 " + rows.size() + " 个点位逐个填写核验结果");
        }
        int passCount = 0;
        int failCount = 0;
        for (DisablePermitPoint row : rows) {
            PermitDtos.ResetItemReq item = itemMap.get(row.getPointId());
            if (item == null) {
                throw new BizException("缺少点位 " + row.getPointId() + " 的核验结果");
            }
            String result = item.getResult();
            if (!"PASS".equals(result) && !"FAIL".equals(result)) {
                throw new BizException("核验结果只能是合格 / 不合格");
            }
            if ("FAIL".equals(result) && (item.getFailReason() == null || item.getFailReason().isBlank())) {
                throw new BizException("点位 " + row.getPointId() + " 核验不合格时必须填写失败原因");
            }
            if ("PASS".equals(result)) {
                passCount++;
            } else {
                failCount++;
            }
        }

        // 落新一轮核验记录（失败修正后可再次核验，历史轮次保留）
        int round = latestRound(id) + 1;
        String batchNo = batchNo(id, round);
        LocalDateTime now = LocalDateTime.now();
        for (DisablePermitPoint row : rows) {
            PermitDtos.ResetItemReq item = itemMap.get(row.getPointId());
            ResetVerification verification = new ResetVerification();
            verification.setBatchNo(batchNo);
            verification.setPermitId(id);
            verification.setPointId(row.getPointId());
            verification.setResult(item.getResult());
            verification.setVerifyDesc(item.getVerifyDesc());
            verification.setFailReason("FAIL".equals(item.getResult()) ? item.getFailReason() : null);
            verification.setVerifiedBy(user.getDisplayName());
            verification.setCreatedAt(now);
            verificationRepository.save(verification);
        }

        String fromStatus = permit.getStatus();
        String failSummary = failCount == 0 ? null : req.getItems().stream()
                .filter(i -> "FAIL".equals(i.getResult()))
                .map(i -> {
                    FacilityPoint p = pointRepository.findById(i.getPointId()).orElse(null);
                    return (p == null ? i.getPointId() : p.getPointCode()) + "：" + i.getFailReason();
                }).collect(Collectors.joining("；"));

        if ("EFFECTIVE".equals(fromStatus)) {
            permit.setStatus("PENDING_RESET");
            permit.setResetStartedAt(now);
        }
        permit.setLastVerifyAt(now);
        permit.setLastVerifyBy(user.getDisplayName());
        permit.setLastVerifyFailReason(failSummary);
        permit.setUpdatedAt(now);
        saveWithVersionGuard(permit);

        if ("EFFECTIVE".equals(fromStatus)) {
            writeLog(id, "RESET_START", "EFFECTIVE", "PENDING_RESET", "发起复位核验（批次 " + batchNo + "）");
        }
        writeLog(id, "RESET_VERIFY", "PENDING_RESET", "PENDING_RESET",
                "第 " + round + " 轮复位核验：合格 " + passCount + " / 不合格 " + failCount
                        + (failSummary == null ? "，全部合格，待消防主管关闭许可" : "；" + failSummary));
        return detail(id);
    }

    /**
     * 关闭许可：只在最新一轮逐点位核验全部合格时允许。关闭动作在同一事务里
     * 解除所有点位的「许可停用」标记，许可状态与设施标记一起落库，不留半成品。
     */
    @Transactional
    public Views.PermitDetailView close(Long id, PermitDtos.CloseReq req) {
        CurrentUserHolder.requireRole("FIRE_MANAGER", "消防主管");
        DisablePermit permit = loadAndCheckVersion(id, req == null ? null : req.getVersion());
        if (!"PENDING_RESET".equals(permit.getStatus())) {
            throw new BizException("只有待复位的许可可以关闭，当前状态：" + FireDict.permitStatusName(permit.getStatus()));
        }
        if (!latestRoundAllPassed(id)) {
            throw new BizException("最新一轮复位核验仍有不合格点位，不能关闭；请整改后重新核验");
        }
        List<Long> pointIds = permitPointRepository.findByPermitIdOrderByIdAsc(id).stream()
                .map(DisablePermitPoint::getPointId).toList();

        // 先解标记再翻状态，同一事务原子提交
        permitRepository.unmarkPoints(id);
        permitRepository.updatePointMarked(id, false);

        LocalDateTime now = LocalDateTime.now();
        permit.setStatus("CLOSED");
        permit.setClosedAt(now);
        permit.setClosedBy(CurrentUserHolder.require().getDisplayName());
        permit.setLastVerifyFailReason(null);
        permit.setUpdatedAt(now);
        saveWithVersionGuard(permit);
        writeLog(id, "CLOSE", "PENDING_RESET", "CLOSED",
                "核验全部合格，关闭许可并解除 " + pointIds.size() + " 个点位的许可停用标记"
                        + (req != null && req.getRemark() != null && !req.getRemark().isBlank()
                        ? "；" + req.getRemark() : ""));
        return detail(id);
    }

    // ==================== 内部工具 ====================

    private record ValidatedScope(Long buildingId, String reason, SysUser siteOwner, List<Long> pointIds) {
    }

    private ValidatedScope validateScope(PermitDtos.SaveReq req, DisablePermit existing) {
        if (req == null) {
            throw new BizException("请求体为空");
        }
        Long buildingId = existing == null ? req.getBuildingId() : existing.getBuildingId();
        if (buildingId == null) {
            throw new BizException("请选择所属建筑");
        }
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BizException("建筑不存在：" + buildingId));
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new BizException("停用原因不能为空");
        }
        if (req.getPlanStart() == null || req.getPlanEnd() == null) {
            throw new BizException("请填写计划起止时间");
        }
        if (req.getPlanEnd().isBefore(req.getPlanStart())) {
            throw new BizException("计划结束时间不能早于开始时间");
        }
        if (req.getSiteOwnerId() == null) {
            throw new BizException("请选择现场负责人");
        }
        SysUser siteOwner = userRepository.findById(req.getSiteOwnerId())
                .orElseThrow(() -> new BizException("现场负责人不存在：" + req.getSiteOwnerId()));
        if (!"SITE_OWNER".equals(siteOwner.getRole())) {
            throw new BizException("现场负责人必须是现场负责人角色，当前选择：" + FireDict.roleName(siteOwner.getRole()));
        }

        List<Long> pointIds = req.getPointIds() == null ? List.of()
                : req.getPointIds().stream().distinct().toList();
        if (pointIds.isEmpty()) {
            throw new BizException("请至少选择一个停用点位");
        }
        List<FacilityPoint> points = pointRepository.findByIdIn(pointIds);
        if (points.size() != pointIds.size()) {
            throw new BizException("部分点位不存在，请刷新后重选");
        }
        for (FacilityPoint point : points) {
            if (!buildingId.equals(point.getBuildingId())) {
                throw new BizException("点位 " + point.getPointCode() + " 不属于所选建筑，一张许可只能覆盖同一栋建筑");
            }
        }
        if (req.getMeasures() != null) {
            for (PermitDtos.MeasureReq m : req.getMeasures()) {
                if (!FireDict.isValidMeasureType(m.getMeasureType())) {
                    throw new BizException("补偿措施类别只能是：替代灭火器 / 加密巡查 / 临时警戒 / 其它");
                }
                if (m.getContent() == null || m.getContent().isBlank()) {
                    throw new BizException("每项补偿措施都必须填写内容");
                }
            }
        }
        return new ValidatedScope(building.getId(), req.getReason().trim(), siteOwner, pointIds);
    }

    private void validateWindow(DisablePermit permit) {
        // 计划开始时间允许是过去（补办场景），但结束时间早于今天的许可不应再提交 / 批准
        if (permit.getPlanEnd().isBefore(LocalDate.now())) {
            throw new BizException("计划结束时间已过，请调整起止时间后再提交");
        }
    }

    /** 批准时排他：同一时间窗内不允许两张占用相同点位的生效许可。 */
    private void ensureNoConcurrentActivePermit(DisablePermit current, List<Long> pointIds) {
        Set<Long> pointSet = new HashSet<>(pointIds);
        for (DisablePermit other : permitRepository.findOverlapping(
                current.getBuildingId(), current.getPlanStart(), current.getPlanEnd())) {
            if (other.getId().equals(current.getId())) {
                continue;
            }
            List<Long> otherPoints = permitPointRepository.findByPermitIdOrderByIdAsc(other.getId()).stream()
                    .map(DisablePermitPoint::getPointId).toList();
            for (Long pid : otherPoints) {
                if (pointSet.contains(pid)) {
                    FacilityPoint p = pointRepository.findById(pid).orElse(null);
                    throw new BizException("点位 " + (p == null ? pid : p.getPointCode())
                            + " 在同一时段已被许可 " + other.getPermitNo() + " 占用");
                }
            }
        }
    }

    /** 现场负责人操作：必须是 SITE_OWNER 且为该许可登记的现场负责人本人。 */
    private SysUser requireSiteOwnerOf(Long permitId) {
        SysUser user = CurrentUserHolder.require();
        if (!"SITE_OWNER".equals(user.getRole())) {
            throw new ForbiddenException("只有现场负责人可以执行该操作，当前身份：" + user.getDisplayName());
        }
        if (permitId != null) {
            DisablePermit permit = ensurePermit(permitId);
            if (!user.getId().equals(permit.getSiteOwnerId())) {
                throw new ForbiddenException("只有该许可登记的现场负责人（" + permit.getSiteOwnerName()
                        + "）可以操作，当前身份：" + user.getDisplayName());
            }
        }
        return user;
    }

    /** 带版本号校验的加载：并发的第二个请求拿到的是旧版本，直接 409。 */
    private DisablePermit loadAndCheckVersion(Long id, Long requestVersion) {
        DisablePermit permit = ensurePermit(id);
        if (requestVersion != null && requestVersion != permit.getVersion()) {
            throw new ConflictException("许可已被其他人更新（版本 v" + permit.getVersion()
                    + "），请刷新详情后重试，本次操作未生效");
        }
        return permit;
    }

    /** save 并把 JPA 乐观锁异常归一为 409（双保险，重复提交不会产生第二条状态记录）。 */
    private void saveWithVersionGuard(DisablePermit permit) {
        try {
            permitRepository.saveAndFlush(permit);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("同一许可的并发操作只有一个能成功，请刷新详情后重试");
        }
    }

    private void savePoints(Long permitId, List<Long> pointIds, LocalDateTime now) {
        for (Long pointId : pointIds) {
            DisablePermitPoint row = new DisablePermitPoint();
            row.setPermitId(permitId);
            row.setPointId(pointId);
            row.setMarked(false);
            row.setCreatedAt(now);
            permitPointRepository.save(row);
        }
    }

    private void saveMeasures(Long permitId, List<PermitDtos.MeasureReq> measures, SysUser user, LocalDateTime now) {
        if (measures == null) {
            return;
        }
        for (PermitDtos.MeasureReq req : measures) {
            DisableMeasure measure = new DisableMeasure();
            measure.setPermitId(permitId);
            measure.setMeasureType(req.getMeasureType());
            measure.setContent(req.getContent());
            measure.setSpecDesc(req.getSpecDesc());
            measure.setCreatedBy(user.getDisplayName());
            measure.setCreatedAt(now);
            measureRepository.save(measure);
        }
    }

    private void writeLog(Long permitId, String action, String from, String to, String remark) {
        SysUser operator = CurrentUserHolder.require();
        PermitActionLog log = new PermitActionLog();
        log.setPermitId(permitId);
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperator(operator.getDisplayName());
        log.setOperatorRole(operator.getRole());
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
    }

    @Transactional(readOnly = true)
    public DisablePermit ensurePermit(Long id) {
        return permitRepository.findById(id)
                .orElseThrow(() -> new BizException("停用许可不存在：" + id));
    }

    private List<Views.PermitOrderView> orderViews(Long permitId) {
        List<Long> pointIds = permitPointRepository.findByPermitIdOrderByIdAsc(permitId).stream()
                .map(DisablePermitPoint::getPointId).toList();
        Map<Long, String> waiverNotes = new HashMap<>();
        for (PermitOrderWaiver w : waiverRepository.findByPermitIdOrderByIdAsc(permitId)) {
            waiverNotes.put(w.getOrderId(), w.getHandleNote());
        }
        List<Views.PermitOrderView> list = new ArrayList<>();
        for (MaintenanceOrder order : orderRepository.findByPointIdInAndStatusIn(pointIds,
                List.of("PENDING", "ASSIGNED", "WORKING", "DONE", "ACCEPTED"))) {
            boolean finished = "DONE".equals(order.getStatus()) || "ACCEPTED".equals(order.getStatus());
            String note = waiverNotes.get(order.getId());
            boolean handled = finished || note != null;
            // 已完成 / 已验收的单子不占用复位前置，列表里只展示真正未完成的
            if (finished) {
                continue;
            }
            list.add(new Views.PermitOrderView(order.getId(), order.getOrderNo(),
                    order.getStatus(), FireDict.orderStatusName(order.getStatus()),
                    order.getFaultDesc(), note, handled));
        }
        return list;
    }

    private List<Views.ActionLogView> logViews(Long permitId) {
        return logRepository.findByPermitIdOrderByIdAsc(permitId).stream()
                .map(l -> new Views.ActionLogView(
                        l.getId(), l.getAction(), FireDict.permitActionName(l.getAction()),
                        l.getFromStatus(), FireDict.permitStatusName(l.getFromStatus()),
                        l.getToStatus(), FireDict.permitStatusName(l.getToStatus()),
                        l.getOperator(), l.getOperatorRole(), FireDict.roleName(l.getOperatorRole()),
                        l.getRemark(), l.getCreatedAt()))
                .toList();
    }

    private Views.PermitListView toListView(DisablePermit permit) {
        List<Long> pointIds = permitPointRepository.findByPermitIdOrderByIdAsc(permit.getId()).stream()
                .map(DisablePermitPoint::getPointId).toList();
        long measureTotal = measureRepository.countByPermitId(permit.getId());
        long measureConfirmed = measureTotal - measureRepository.countByPermitIdAndConfirmedAtIsNull(permit.getId());
        long unfinished = orderRepository.findByPointIdInAndStatusIn(pointIds, UNFINISHED_ORDER_STATUS).stream()
                .filter(o -> waiverRepository.findByPermitIdAndOrderId(permit.getId(), o.getId()).isEmpty())
                .count();
        Building building = buildingRepository.findById(permit.getBuildingId()).orElse(null);
        return new Views.PermitListView(
                permit.getId(), permit.getPermitNo(), permit.getBuildingId(),
                building == null ? "" : building.getName(),
                permit.getStatus(), FireDict.permitStatusName(permit.getStatus()),
                permit.getReason(), permit.getPlanStart(), permit.getPlanEnd(),
                permit.getSiteOwnerId(), permit.getSiteOwnerName(), permit.getCreatedBy(),
                pointIds.size(), measureTotal, measureConfirmed, unfinished,
                isExpired(permit), isExpiringSoon(permit),
                "PENDING_RESET".equals(permit.getStatus()) && latestRoundAllPassed(permit.getId()),
                permit.getVersion(), permit.getCreatedAt());
    }

    private boolean isExpired(DisablePermit permit) {
        return ACTIVE_STATUSES.contains(permit.getStatus())
                && permit.getPlanEnd() != null && permit.getPlanEnd().isBefore(LocalDate.now());
    }

    private boolean isExpiringSoon(DisablePermit permit) {
        if (!"EFFECTIVE".equals(permit.getStatus())) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalDate end = permit.getPlanEnd();
        return end != null && !end.isBefore(today) && !end.isAfter(today.plusDays(EXPIRE_SOON_DAYS));
    }

    private int latestRound(Long permitId) {
        return verificationRepository.findByPermitIdOrderByIdAsc(permitId).stream()
                .mapToInt(v -> batchRound(v.getBatchNo())).max().orElse(0);
    }

    /** 最新一轮是否逐点位全部合格（且覆盖全部点位）。 */
    private boolean latestRoundAllPassed(Long permitId) {
        int round = latestRound(permitId);
        if (round == 0) {
            return false;
        }
        List<ResetVerification> batch = verificationRepository
                .findByPermitIdAndBatchNoOrderByIdAsc(permitId, batchNo(permitId, round));
        long total = permitPointRepository.countByPermitId(permitId);
        return total > 0 && batch.size() == total && batch.stream().allMatch(v -> "PASS".equals(v.getResult()));
    }

    private static String batchNo(Long permitId, int round) {
        return "RV-" + permitId + "-" + String.format("%02d", round);
    }

    private static int batchRound(String batchNo) {
        try {
            return Integer.parseInt(batchNo.substring(batchNo.lastIndexOf('-') + 1));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private String nextPermitNo() {
        String prefix = FireDict.PERMIT_NO_PREFIX + "-" + LocalDate.now().getYear() + "-";
        long max = 0L;
        for (DisablePermit permit : permitRepository.findAll()) {
            String no = permit.getPermitNo();
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
