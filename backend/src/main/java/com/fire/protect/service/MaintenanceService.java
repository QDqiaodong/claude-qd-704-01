package com.fire.protect.service;

import java.math.BigDecimal;
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
import com.fire.protect.domain.Extinguisher;
import com.fire.protect.domain.FacilityPoint;
import com.fire.protect.domain.FireDict;
import com.fire.protect.domain.MaintenanceOrder;
import com.fire.protect.dto.MaintenanceDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.repository.BuildingRepository;
import com.fire.protect.repository.DisablePermitRepository;
import com.fire.protect.repository.ExtinguisherRepository;
import com.fire.protect.repository.FacilityPointRepository;
import com.fire.protect.repository.MaintenanceOrderRepository;

/** 模块四：维保单，状态流转 待派工 → 已派工 → 处理中 → 已完成 → 已验收。 */
@Service
public class MaintenanceService {

    private final MaintenanceOrderRepository maintenanceOrderRepository;
    private final FacilityPointRepository facilityPointRepository;
    private final ExtinguisherRepository extinguisherRepository;
    private final BuildingRepository buildingRepository;
    private final DisablePermitRepository disablePermitRepository;

    public MaintenanceService(MaintenanceOrderRepository maintenanceOrderRepository,
                              FacilityPointRepository facilityPointRepository,
                              ExtinguisherRepository extinguisherRepository,
                              BuildingRepository buildingRepository,
                              DisablePermitRepository disablePermitRepository) {
        this.maintenanceOrderRepository = maintenanceOrderRepository;
        this.facilityPointRepository = facilityPointRepository;
        this.extinguisherRepository = extinguisherRepository;
        this.buildingRepository = buildingRepository;
        this.disablePermitRepository = disablePermitRepository;
    }

    @Transactional(readOnly = true)
    public List<Views.MaintenanceOrderView> list() {
        List<MaintenanceOrder> orders = maintenanceOrderRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        return toViews(orders);
    }

    /** 三列看板：待派工 / 处理中（已派工 + 处理中）/ 已完成（已完成 + 已验收）。 */
    @Transactional(readOnly = true)
    public Views.MaintenanceBoardDTO board() {
        List<MaintenanceOrder> orders = maintenanceOrderRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        List<Views.MaintenanceOrderView> views = toViews(orders);
        BigDecimal total = BigDecimal.ZERO;
        for (Views.MaintenanceOrderView view : views) {
            total = total.add(nz(view.totalFee()));
        }
        List<Views.MaintenanceOrderView> pending = views.stream()
                .filter(v -> "PENDING".equals(v.status())).toList();
        List<Views.MaintenanceOrderView> working = views.stream()
                .filter(v -> "ASSIGNED".equals(v.status()) || "WORKING".equals(v.status())).toList();
        List<Views.MaintenanceOrderView> done = views.stream()
                .filter(v -> "DONE".equals(v.status()) || "ACCEPTED".equals(v.status())).toList();
        return new Views.MaintenanceBoardDTO(pending, working, done, total);
    }

    /** 新建维保单：单号由 service 生成（MO-年-四位序号），关联点位或气瓶二选一。 */
    @Transactional
    public Views.MaintenanceOrderView create(MaintenanceDtos.CreateReq req) {
        if (!FireDict.isValidOrderType(req.getOrderType())) {
            throw new BizException("维保类型只能是：维修 / 更换 / 充装 / 检测");
        }
        Long pointId = req.getPointId();
        Long extId = req.getExtId();
        if (pointId != null) {
            facilityPointRepository.findById(pointId).orElseThrow(() -> new BizException("关联点位不存在：" + pointId));
        }
        if (extId != null) {
            extinguisherRepository.findById(extId).orElseThrow(() -> new BizException("关联气瓶不存在：" + extId));
        }
        if (pointId == null && extId == null) {
            throw new BizException("维保单必须关联一个点位或一台气瓶");
        }
        if (req.getMaterialFee() != null && req.getMaterialFee().signum() < 0) {
            throw new BizException("材料费不能为负");
        }
        if (req.getLaborFee() != null && req.getLaborFee().signum() < 0) {
            throw new BizException("人工费不能为负");
        }

        LocalDateTime now = LocalDateTime.now();
        MaintenanceOrder order = new MaintenanceOrder();
        order.setOrderNo(nextOrderNo());
        order.setOrderType(req.getOrderType());
        order.setPointId(pointId);
        order.setExtId(extId);
        order.setFaultDesc(req.getFaultDesc());
        order.setMaterialFee(nz(req.getMaterialFee()));
        order.setLaborFee(nz(req.getLaborFee()));
        order.setTotalFee(order.getMaterialFee().add(order.getLaborFee()));
        order.setVendor(req.getVendor());
        order.setOwnerPerson(req.getOwnerPerson());
        order.setStatus("PENDING");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        MaintenanceOrder saved = maintenanceOrderRepository.save(order);
        return toView(saved, new HashMap<>(), new HashMap<>());
    }

    /** 派工：待派工 → 已派工，必须给到服务商与责任人。 */
    @Transactional
    public Views.MaintenanceOrderView assign(Long id, MaintenanceDtos.AssignReq req) {
        MaintenanceOrder order = ensureOrder(id);
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException("只有待派工的维保单可以派工");
        }
        order.setVendor(req.getVendor());
        order.setOwnerPerson(req.getOwnerPerson());
        order.setStatus("ASSIGNED");
        order.setUpdatedAt(LocalDateTime.now());
        maintenanceOrderRepository.save(order);
        return toView(order, new HashMap<>(), new HashMap<>());
    }

    /**
     * 状态流转：一次推进一档（处理中 / 已完成 / 已验收）。
     * 完工必须写处理措施；只有已完成的单子才能验收。
     */
    @Transactional
    public Views.MaintenanceOrderView advance(Long id, MaintenanceDtos.StatusReq body) {
        String target = body == null ? null : body.getTarget();
        MaintenanceOrder order = ensureOrder(id);
        if (!FireDict.isValidOrderStatus(target)) {
            throw new BizException("目标状态不存在：" + target);
        }
        int currentIndex = FireDict.ORDER_STATUS_ORDER.indexOf(order.getStatus());
        int targetIndex = FireDict.ORDER_STATUS_ORDER.indexOf(target);
        if (targetIndex <= currentIndex) {
            throw new BizException("维保单状态只能向前流转，当前处于" + FireDict.orderStatusName(order.getStatus()));
        }
        if ("ASSIGNED".equals(target) && (isBlank(order.getVendor()) || isBlank(order.getOwnerPerson()))) {
            throw new BizException("派工前请先指定服务商与责任人");
        }
        if ("DONE".equals(target)) {
            if (body != null && body.getSolution() != null) {
                order.setSolution(body.getSolution());
            }
            if (body != null && body.getMaterialFee() != null) {
                order.setMaterialFee(body.getMaterialFee());
            }
            if (body != null && body.getLaborFee() != null) {
                order.setLaborFee(body.getLaborFee());
            }
            if (isBlank(order.getSolution())) {
                throw new BizException("完工前必须填写处理措施");
            }
            order.setFinishDate(LocalDate.now());
        }
        if ("ACCEPTED".equals(target) && !"DONE".equals(order.getStatus())) {
            throw new BizException("只有已完成的维保单才能验收");
        }
        order.setTotalFee(nz(order.getMaterialFee()).add(nz(order.getLaborFee())));
        order.setStatus(target);
        order.setUpdatedAt(LocalDateTime.now());
        MaintenanceOrder saved = maintenanceOrderRepository.save(order);
        return toView(saved, new HashMap<>(), new HashMap<>());
    }

    /** 为巡检发现的异常点位补一张维修单（巡检完成时自动调用）。 */
    @Transactional
    public MaintenanceOrder createForInspection(Long pointId, LocalDate inspectDate, String faultDesc) {
        LocalDateTime now = LocalDateTime.now();
        MaintenanceOrder order = new MaintenanceOrder();
        order.setOrderNo(nextOrderNo());
        order.setOrderType("REPAIR");
        order.setPointId(pointId);
        order.setFaultDesc(faultDesc == null || faultDesc.isBlank() ? "巡检发现异常，待现场确认" : faultDesc);
        order.setMaterialFee(BigDecimal.ZERO);
        order.setLaborFee(BigDecimal.ZERO);
        order.setTotalFee(BigDecimal.ZERO);
        order.setVendor("待指派");
        order.setOwnerPerson("待指派");
        order.setStatus("PENDING");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return maintenanceOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public MaintenanceOrder ensureOrder(Long id) {
        return maintenanceOrderRepository.findById(id).orElseThrow(() -> new BizException("维保单不存在：" + id));
    }

    private List<Views.MaintenanceOrderView> toViews(List<MaintenanceOrder> orders) {
        Map<Long, FacilityPoint> pointMap = new HashMap<>();
        Map<Long, Extinguisher> extMap = new HashMap<>();
        for (MaintenanceOrder order : orders) {
            if (order.getPointId() != null) {
                facilityPointRepository.findById(order.getPointId()).ifPresent(p -> pointMap.put(p.getId(), p));
            }
            if (order.getExtId() != null) {
                extinguisherRepository.findById(order.getExtId()).ifPresent(e -> extMap.put(e.getId(), e));
            }
        }
        List<Views.MaintenanceOrderView> list = new ArrayList<>();
        for (MaintenanceOrder order : orders) {
            list.add(toView(order, pointMap, extMap));
        }
        return list;
    }

    private Views.MaintenanceOrderView toView(MaintenanceOrder order,
                                             Map<Long, FacilityPoint> pointMap,
                                             Map<Long, Extinguisher> extMap) {
        FacilityPoint point = order.getPointId() == null ? null : pointMap.get(order.getPointId());
        Extinguisher cylinder = order.getExtId() == null ? null : extMap.get(order.getExtId());
        String targetName;
        String buildingName = "";
        if (point != null) {
            targetName = point.getPointCode() + " " + FireDict.facilityTypeName(point.getFacilityType());
            Building building = buildingRepository.findById(point.getBuildingId()).orElse(null);
            buildingName = building == null ? "" : building.getName();
        } else if (cylinder != null) {
            targetName = cylinder.getSteelNo() + " " + FireDict.cylinderTypeName(cylinder.getCylType());
            Building building = buildingRepository.findById(cylinder.getBuildingId()).orElse(null);
            buildingName = building == null ? "" : building.getName();
        } else {
            targetName = "未关联设施";
        }
        String next = FireDict.nextOrderStatus(order.getStatus());
        String pointPermitNo = null;
        if (point != null && point.getActiveDisablePermitId() != null) {
            pointPermitNo = disablePermitRepository.findById(point.getActiveDisablePermitId())
                    .map(com.fire.protect.domain.DisablePermit::getPermitNo).orElse(null);
        }
        return new Views.MaintenanceOrderView(
                order.getId(), order.getOrderNo(), order.getOrderType(),
                FireDict.orderTypeName(order.getOrderType()),
                order.getPointId(), point == null ? null : point.getPointCode(),
                order.getExtId(), cylinder == null ? null : cylinder.getPointCode(),
                targetName, buildingName,
                order.getFaultDesc(), order.getSolution(),
                nz(order.getMaterialFee()), nz(order.getLaborFee()), nz(order.getTotalFee()),
                order.getVendor(), order.getOwnerPerson(), order.getFinishDate(),
                order.getStatus(), FireDict.orderStatusName(order.getStatus()),
                next, next == null ? "" : FireDict.orderStatusName(next),
                pointPermitNo);
    }

    /** MO-2026-0007 这种流水号：按当年已建单量 + 1 */
    private String nextOrderNo() {
        String prefix = FireDict.ORDER_NO_PREFIX + "-" + LocalDate.now().getYear() + "-";
        long max = 0L;
        for (MaintenanceOrder order : maintenanceOrderRepository.findAll()) {
            String no = order.getOrderNo();
            if (no != null && no.length() > 4 && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Long.parseLong(no.substring(no.length() - 4)));
                } catch (NumberFormatException ignored) {
                    // 老格式单号不计入
                }
            }
        }
        return prefix + String.format("%04d", max + 1);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
