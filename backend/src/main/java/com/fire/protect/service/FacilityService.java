package com.fire.protect.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fire.protect.common.BizException;
import com.fire.protect.domain.Building;
import com.fire.protect.domain.BuildingFloor;
import com.fire.protect.domain.DisablePermit;
import com.fire.protect.domain.FacilityPoint;
import com.fire.protect.domain.FireDict;
import com.fire.protect.dto.FacilityDtos;
import com.fire.protect.dto.MaintenanceDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.repository.BuildingFloorRepository;
import com.fire.protect.repository.BuildingRepository;
import com.fire.protect.repository.DisablePermitRepository;
import com.fire.protect.repository.FacilityPointRepository;

/**
 * 模块二：消防设施点位。
 *
 * <p>写入两侧的通用计算（点位编码、下次检查日、过期回落状态）全部在
 * {@link com.fire.protect.listener.FacilityLifecycleListener} 里由 {@code @PrePersist}/{@code @PreUpdate} 完成，
 * 这里只负责校验、挑出实体、改业务字段然后 save。
 */
@Service
public class FacilityService {

    private final FacilityPointRepository facilityPointRepository;
    private final BuildingRepository buildingRepository;
    private final BuildingFloorRepository buildingFloorRepository;
    private final BuildingService buildingService;
    private final MaintenanceService maintenanceService;
    private final DisablePermitRepository disablePermitRepository;

    public FacilityService(FacilityPointRepository facilityPointRepository,
                           BuildingRepository buildingRepository,
                           BuildingFloorRepository buildingFloorRepository,
                           BuildingService buildingService,
                           MaintenanceService maintenanceService,
                           DisablePermitRepository disablePermitRepository) {
        this.facilityPointRepository = facilityPointRepository;
        this.buildingRepository = buildingRepository;
        this.buildingFloorRepository = buildingFloorRepository;
        this.buildingService = buildingService;
        this.maintenanceService = maintenanceService;
        this.disablePermitRepository = disablePermitRepository;
    }

    /** 当前挂着许可停用标记的许可 id → 许可编号，供平面图 / 台账标注。 */
    private Map<Long, String> permitNoMap() {
        Map<Long, String> map = new HashMap<>();
        for (DisablePermit permit : disablePermitRepository.findAll()) {
            map.put(permit.getId(), permit.getPermitNo());
        }
        return map;
    }

    /** 点位列表：可按建筑 / 楼层 / 设施类型过滤。 */
    @Transactional(readOnly = true)
    public List<Views.FacilityView> list(Long buildingId, Long floorId, String facilityType) {
        List<FacilityPoint> points;
        if (buildingId != null && floorId != null) {
            points = facilityPointRepository.findAllByBuildingIdAndFloorId(buildingId, floorId, Sort.by("id"));
        } else if (buildingId != null) {
            points = facilityPointRepository.findAllByBuildingId(buildingId, Sort.by("id"));
        } else {
            points = facilityPointRepository.findAll(Sort.by("id"));
        }
        Map<Long, String> permitNoMap = permitNoMap();
        List<Views.FacilityView> list = new ArrayList<>();
        Map<Long, Building> buildingMap = new HashMap<>();
        Map<Long, BuildingFloor> floorMap = new HashMap<>();
        for (FacilityPoint point : points) {
            if (facilityType != null && !facilityType.isBlank() && !facilityType.equals(point.getFacilityType())) {
                continue;
            }
            Building building = buildingMap.computeIfAbsent(point.getBuildingId(), id -> buildingRepository.findById(id).orElse(null));
            BuildingFloor floor = floorMap.computeIfAbsent(point.getFloorId(), id -> buildingFloorRepository.findById(id).orElse(null));
            list.add(toView(point, building, floor, permitNoMap));
        }
        return list;
    }

    /** 平面图主视觉：建筑 + 楼层标签 + 全部点位 + 状态统计。 */
    @Transactional(readOnly = true)
    public Views.PlanViewDTO plan(Long buildingId) {
        Building building = buildingService.ensureBuilding(buildingId);
        List<Views.FloorView> floors = buildingService.floorsOf(buildingId);
        List<Views.FacilityView> points = list(buildingId, null, null);

        long total = points.size();
        long normal = 0;
        long replace = 0;
        long fault = 0;
        long disabled = 0;
        long overdue = 0;
        long permitDisabled = 0;
        for (Views.FacilityView point : points) {
            switch (point.status()) {
                case "NORMAL" -> normal++;
                case "REPLACE" -> replace++;
                case "FAULT" -> fault++;
                case "DISABLED" -> disabled++;
                default -> {
                }
            }
            if (point.overdue()) {
                overdue++;
            }
            if (point.permitDisabled()) {
                permitDisabled++;
            }
        }
        return new Views.PlanViewDTO(
                building.getId(), building.getName(), FireDict.buildingTypeName(building.getBuildingType()),
                floors, points,
                new Views.PlanSummary(total, normal, replace, fault, disabled, overdue, permitDisabled));
    }

    /**
     * 新增点位：只需要给建筑、楼层、类型与位置，
     * 点位编码、检查周期、下次检查日全部交给 {@code @PrePersist}。
     */
    @Transactional
    public Views.FacilityView create(FacilityDtos.CreateReq req) {
        Building building = buildingService.ensureBuilding(req.getBuildingId());
        if (!FireDict.isValidFacilityType(req.getFacilityType())) {
            throw new BizException("设施类型只能是：灭火器 / 消火栓 / 喷淋头 / 烟感探测器 / 手动报警按钮 / 应急照明 / 防火门 / 消防泵");
        }
        BuildingFloor floor = buildingFloorRepository.findById(req.getFloorId())
                .orElseThrow(() -> new BizException("楼层不存在：" + req.getFloorId()));
        if (!floor.getBuildingId().equals(building.getId())) {
            throw new BizException("所选楼层不属于该建筑");
        }
        if (req.getStatus() != null && !FireDict.isValidPointStatus(req.getStatus())) {
            throw new BizException("点位状态只能是：正常 / 待更换 / 故障 / 停用");
        }

        FacilityPoint point = new FacilityPoint();
        point.setBuildingId(building.getId());
        point.setFloorId(floor.getId());
        point.setFacilityType(req.getFacilityType());
        point.setSpec(req.getSpec());
        point.setLocationDesc(req.getLocationDesc());
        point.setInstallDate(req.getInstallDate());
        point.setCheckCycleDays(req.getCheckCycleDays());
        point.setPlanX(req.getPlanX() == null ? 500 : req.getPlanX());
        point.setPlanY(req.getPlanY() == null ? 340 : req.getPlanY());
        point.setStatus(req.getStatus() == null ? "NORMAL" : req.getStatus());
        FacilityPoint saved = facilityPointRepository.save(point);
        return toView(saved, building, floor, permitNoMap());
    }

    /**
     * 标记已检：只写「上次检查日」，下次检查日由 {@code @PreUpdate} 按检查周期重算，
     * 并自动解除「待更换」这类过期回落状态。
     */
    @Transactional
    public Views.FacilityView markChecked(Long id, LocalDate date) {
        FacilityPoint point = ensurePoint(id);
        if ("DISABLED".equals(point.getStatus())) {
            throw new BizException("点位已停用，不能标记已检");
        }
        if (point.getActiveDisablePermitId() != null) {
            throw new BizException("点位处于许可停用期间，待许可关闭复位后再标记检查；期间巡检异常照常记录");
        }
        point.setLastCheckDate(date == null ? LocalDate.now() : date);
        if (!"FAULT".equals(point.getStatus())) {
            point.setStatus("NORMAL");
        }
        // saveAndFlush：让 @PreUpdate 当场跑完，返回的下次检查日就是重算后的值
        FacilityPoint saved = facilityPointRepository.saveAndFlush(point);
        return toView(saved, buildingRepository.findById(saved.getBuildingId()).orElse(null),
                buildingFloorRepository.findById(saved.getFloorId()).orElse(null), permitNoMap());
    }

    /** 图上直接报修：拉张维保单；许可停用期间只登记工单，不改设施原状态。 */
    @Transactional
    public Views.MaintenanceOrderView report(Long id, MaintenanceDtos.CreateReq req) {
        FacilityPoint point = ensurePoint(id);
        MaintenanceDtos.CreateReq body = req == null ? new MaintenanceDtos.CreateReq() : req;
        body.setPointId(point.getId());
        if (body.getOrderType() == null || body.getOrderType().isBlank()) {
            body.setOrderType("REPAIR");
        }
        Views.MaintenanceOrderView order = maintenanceService.create(body);
        boolean frozen = point.getActiveDisablePermitId() != null;
        if (!frozen && body.getFaultDesc() != null && !body.getFaultDesc().isBlank()
                && !"DISABLED".equals(point.getStatus())) {
            point.setStatus("FAULT");
            facilityPointRepository.saveAndFlush(point);
        }
        return order;
    }

    @Transactional(readOnly = true)
    public FacilityPoint ensurePoint(Long id) {
        return facilityPointRepository.findById(id).orElseThrow(() -> new BizException("点位不存在：" + id));
    }

    private Views.FacilityView toView(FacilityPoint point, Building building, BuildingFloor floor,
                                      Map<Long, String> permitNoMap) {
        LocalDate today = LocalDate.now();
        LocalDate next = point.getNextCheckDate();
        Long permitId = point.getActiveDisablePermitId();
        return new Views.FacilityView(
                point.getId(), point.getPointCode(), point.getBuildingId(),
                building == null ? "" : building.getName(),
                point.getFloorId(),
                floor == null ? "" : floor.getFloorCode(),
                floor == null ? "" : floor.getFloorName(),
                point.getFacilityType(), FireDict.facilityTypeName(point.getFacilityType()),
                point.getSpec(), point.getLocationDesc(), point.getInstallDate(),
                point.getCheckCycleDays(), point.getLastCheckDate(), next,
                point.getStatus(), FireDict.pointStatusName(point.getStatus()),
                point.getPlanX(), point.getPlanY(),
                next == null ? null : ChronoUnit.DAYS.between(today, next),
                next != null && next.isBefore(today),
                permitId,
                permitId == null ? null : permitNoMap.getOrDefault(permitId, ""),
                permitId != null);
    }
}
