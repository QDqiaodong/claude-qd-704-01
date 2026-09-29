package com.fire.protect.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.fire.protect.domain.Inspection;
import com.fire.protect.domain.InspectionItem;
import com.fire.protect.dto.InspectionDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.repository.BuildingFloorRepository;
import com.fire.protect.repository.BuildingRepository;
import com.fire.protect.repository.DisablePermitRepository;
import com.fire.protect.repository.FacilityPointRepository;
import com.fire.protect.repository.InspectionItemRepository;
import com.fire.protect.repository.InspectionRepository;

/**
 * 模块三：巡检记录（主表 + 明细行）。
 *
 * <p>新建巡检时按「非停用点位」自动铺开明细行；完成巡检时会把每个「正常」点位的
 * 上次检查日写成巡检日期 —— 之后的重算由 {@code @PreUpdate} 兜住，service 不参与。
 */
@Service
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final InspectionItemRepository inspectionItemRepository;
    private final FacilityPointRepository facilityPointRepository;
    private final BuildingRepository buildingRepository;
    private final BuildingFloorRepository buildingFloorRepository;
    private final BuildingService buildingService;
    private final MaintenanceService maintenanceService;
    private final DisablePermitRepository disablePermitRepository;

    public InspectionService(InspectionRepository inspectionRepository,
                             InspectionItemRepository inspectionItemRepository,
                             FacilityPointRepository facilityPointRepository,
                             BuildingRepository buildingRepository,
                             BuildingFloorRepository buildingFloorRepository,
                             BuildingService buildingService,
                             MaintenanceService maintenanceService,
                             DisablePermitRepository disablePermitRepository) {
        this.inspectionRepository = inspectionRepository;
        this.inspectionItemRepository = inspectionItemRepository;
        this.facilityPointRepository = facilityPointRepository;
        this.buildingRepository = buildingRepository;
        this.buildingFloorRepository = buildingFloorRepository;
        this.buildingService = buildingService;
        this.maintenanceService = maintenanceService;
        this.disablePermitRepository = disablePermitRepository;
    }

    /** 巡检列表：每行自带明细，页面展开行直接渲染。 */
    @Transactional(readOnly = true)
    public List<Views.InspectionView> list(Long buildingId) {
        List<Inspection> inspections = buildingId == null
                ? inspectionRepository.findAll(Sort.by(Sort.Direction.DESC, "inspectDate", "id"))
                : inspectionRepository.findAllByBuildingId(buildingId, Sort.by(Sort.Direction.DESC, "inspectDate", "id"));
        List<Views.InspectionView> list = new ArrayList<>();
        for (Inspection inspection : inspections) {
            list.add(toView(inspection, itemsOf(inspection.getId())));
        }
        return list;
    }

    /**
     * 新建巡检：拉出该建筑（或指定楼层）的非停用点位，逐条生成待检明细行。
     */
    @Transactional
    public Views.InspectionView create(InspectionDtos.CreateReq req) {
        Building building = buildingService.ensureBuilding(req.getBuildingId());
        if (!FireDict.isValidInspectionType(req.getInspectType())) {
            throw new BizException("巡检类型只能是：日巡 / 月检 / 季检 / 年检");
        }
        if (req.getInspector() == null || req.getInspector().isBlank()) {
            throw new BizException("巡检人不能为空");
        }

        List<FacilityPoint> points = facilityPointRepository.findAllByBuildingId(building.getId(), Sort.by("id")).stream()
                .filter(point -> !"DISABLED".equals(point.getStatus()))
                .filter(point -> req.getFloorId() == null || req.getFloorId().equals(point.getFloorId()))
                .toList();
        if (points.isEmpty()) {
            throw new BizException("该范围内没有可巡检的在册点位");
        }

        LocalDateTime now = LocalDateTime.now();
        Inspection inspection = new Inspection();
        inspection.setInspectNo(nextInspectNo());
        inspection.setBuildingId(building.getId());
        inspection.setInspector(req.getInspector());
        inspection.setInspectDate(req.getInspectDate() == null ? LocalDate.now() : req.getInspectDate());
        inspection.setInspectType(req.getInspectType());
        inspection.setExpectedCount(points.size());
        inspection.setActualCount(0);
        inspection.setAbnormalCount(0);
        inspection.setStatus("PROCESSING");
        inspection.setRemark(req.getRemark());
        inspection.setCreatedAt(now);
        inspection.setUpdatedAt(now);
        Inspection saved = inspectionRepository.save(inspection);

        for (FacilityPoint point : points) {
            InspectionItem item = new InspectionItem();
            item.setInspectionId(saved.getId());
            item.setPointId(point.getId());
            item.setResult("PENDING");
            item.setProblemDesc(null);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            inspectionItemRepository.save(item);
        }
        return toView(saved, itemsOf(saved.getId()));
    }

    /** 记录一行点位结果，并滚动重算实检数 / 异常数。 */
    @Transactional
    public Views.InspectionView saveItem(Long inspectionId, Long itemId, InspectionDtos.ItemResultReq req) {
        Inspection inspection = ensureInspection(inspectionId);
        if ("COMPLETED".equals(inspection.getStatus())) {
            throw new BizException("巡检单已完成，不能再改明细");
        }
        InspectionItem item = inspectionItemRepository.findById(itemId)
                .orElseThrow(() -> new BizException("巡检明细不存在：" + itemId));
        if (!item.getInspectionId().equals(inspectionId)) {
            throw new BizException("该明细不属于这张巡检单");
        }
        if (!FireDict.isValidItemResult(req.getResult())) {
            throw new BizException("巡检结果只能是：正常 / 异常 / 未检");
        }
        if ("ABNORMAL".equals(req.getResult()) && (req.getProblemDesc() == null || req.getProblemDesc().isBlank())) {
            throw new BizException("判为异常时必须填写异常描述");
        }
        item.setResult(req.getResult());
        item.setProblemDesc("ABNORMAL".equals(req.getResult()) ? req.getProblemDesc() : null);
        item.setUpdatedAt(LocalDateTime.now());
        inspectionItemRepository.save(item);
        recount(inspection);
        return toView(inspection, itemsOf(inspection.getId()));
    }

    /**
     * 完成巡检：要求明细全部有结论；正常点位把「上次检查日」写成巡检日期交给
     * {@code @PreUpdate} 重算下次检查日，异常点位转为故障并自动补一张维修单。
     */
    @Transactional
    public Views.InspectionView complete(Long inspectionId) {
        Inspection inspection = ensureInspection(inspectionId);
        if ("COMPLETED".equals(inspection.getStatus())) {
            throw new BizException("巡检单已经完成了");
        }
        List<InspectionItem> items = inspectionItemRepository.findAllByInspectionIdOrderByIdAsc(inspectionId);
        long pending = items.stream().filter(item -> "PENDING".equals(item.getResult())).count();
        if (pending > 0) {
            throw new BizException("还有 " + pending + " 个点位未检，不能收单");
        }
        LocalDate checkedAt = inspection.getInspectDate();
        for (InspectionItem item : items) {
            FacilityPoint point = facilityPointRepository.findById(item.getPointId()).orElse(null);
            if (point == null) {
                continue;
            }
            if (point.getActiveDisablePermitId() != null) {
                // 许可停用期间：巡检结论与维保单照常记录，但不覆盖设施原状态，也不写检查日
                if ("ABNORMAL".equals(item.getResult())) {
                    maintenanceService.createForInspection(point.getId(), checkedAt, item.getProblemDesc());
                }
                continue;
            }
            point.setLastCheckDate(checkedAt);
            if ("NORMAL".equals(item.getResult())) {
                if (!"FAULT".equals(point.getStatus())) {
                    point.setStatus("NORMAL");
                }
            } else {
                point.setStatus("FAULT");
            }
            facilityPointRepository.saveAndFlush(point);
            if ("ABNORMAL".equals(item.getResult())) {
                maintenanceService.createForInspection(point.getId(), checkedAt, item.getProblemDesc());
            }
        }
        recount(inspection);
        inspection.setStatus("COMPLETED");
        inspection.setUpdatedAt(LocalDateTime.now());
        Inspection saved = inspectionRepository.save(inspection);
        return toView(saved, itemsOf(saved.getId()));
    }

    private void recount(Inspection inspection) {
        List<InspectionItem> items = inspectionItemRepository.findAllByInspectionIdOrderByIdAsc(inspection.getId());
        inspection.setExpectedCount(items.size());
        inspection.setActualCount((int) items.stream().filter(i -> !"PENDING".equals(i.getResult())).count());
        inspection.setAbnormalCount((int) items.stream().filter(i -> "ABNORMAL".equals(i.getResult())).count());
        inspection.setUpdatedAt(LocalDateTime.now());
        inspectionRepository.saveAndFlush(inspection);
    }

    @Transactional(readOnly = true)
    public List<Views.InspectionItemView> itemsOf(Long inspectionId) {
        Map<Long, FacilityPoint> pointMap = new HashMap<>();
        Map<Long, BuildingFloor> floorMap = new HashMap<>();
        Map<Long, String> permitNoMap = new HashMap<>();
        List<Views.InspectionItemView> list = new ArrayList<>();
        for (InspectionItem item : inspectionItemRepository.findAllByInspectionIdOrderByIdAsc(inspectionId)) {
            FacilityPoint point = facilityPointRepository.findById(item.getPointId()).orElse(null);
            if (point != null) {
                pointMap.put(point.getId(), point);
                buildingFloorRepository.findById(point.getFloorId()).ifPresent(f -> floorMap.put(f.getId(), f));
                if (point.getActiveDisablePermitId() != null) {
                    disablePermitRepository.findById(point.getActiveDisablePermitId())
                            .ifPresent(p -> permitNoMap.put(point.getId(), p.getPermitNo()));
                }
            }
            String result = item.getResult() == null ? "PENDING" : item.getResult();
            list.add(new Views.InspectionItemView(
                    item.getId(), item.getInspectionId(), item.getPointId(),
                    point == null ? "" : point.getPointCode(),
                    point == null ? "" : FireDict.facilityTypeName(point.getFacilityType()),
                    point == null ? "" : point.getLocationDesc(),
                    point == null || floorMap.get(point.getFloorId()) == null ? "" : floorMap.get(point.getFloorId()).getFloorCode(),
                    result, FireDict.itemResultName(result), item.getProblemDesc(),
                    point == null ? null : permitNoMap.get(point.getId())));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public Inspection ensureInspection(Long id) {
        return inspectionRepository.findById(id).orElseThrow(() -> new BizException("巡检单不存在：" + id));
    }

    private Views.InspectionView toView(Inspection inspection, List<Views.InspectionItemView> items) {
        Building building = buildingRepository.findById(inspection.getBuildingId()).orElse(null);
        return new Views.InspectionView(
                inspection.getId(), inspection.getInspectNo(), inspection.getBuildingId(),
                building == null ? "" : building.getName(),
                inspection.getInspector(), inspection.getInspectDate(), inspection.getInspectType(),
                FireDict.inspectionTypeName(inspection.getInspectType()),
                inspection.getExpectedCount(), inspection.getActualCount(), inspection.getAbnormalCount(),
                inspection.getStatus(), FireDict.inspectionStatusName(inspection.getStatus()),
                inspection.getRemark(), items);
    }

    /** INS-2026-0009 这种流水号 */
    private String nextInspectNo() {
        String prefix = FireDict.INSPECTION_NO_PREFIX + "-" + LocalDate.now().getYear() + "-";
        long max = 0L;
        for (Inspection inspection : inspectionRepository.findAll()) {
            String no = inspection.getInspectNo();
            if (no != null && no.length() > 4 && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Long.parseLong(no.substring(no.length() - 4)));
                } catch (NumberFormatException ignored) {
                    // 历史格式单号不计入
                }
            }
        }
        return prefix + String.format("%04d", max + 1);
    }
}
