package com.fire.protect.service;

import java.math.BigDecimal;
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
import com.fire.protect.domain.Extinguisher;
import com.fire.protect.domain.FacilityPoint;
import com.fire.protect.domain.FireDict;
import com.fire.protect.dto.ExtinguisherDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.repository.BuildingFloorRepository;
import com.fire.protect.repository.BuildingRepository;
import com.fire.protect.repository.ExtinguisherRepository;
import com.fire.protect.repository.FacilityPointRepository;

/**
 * 模块五：灭火器与气瓶台账。
 *
 * <p>气瓶同样走 {@code BaseFacility} 的生命周期回调：月度检查的下次日期写在
 * {@code next_patrol_on}，一旦按上次检查日重算后发现过期就会自动回落到「待充装」。
 */
@Service
public class ExtinguisherService {

    private final ExtinguisherRepository extinguisherRepository;
    private final BuildingRepository buildingRepository;
    private final BuildingFloorRepository buildingFloorRepository;
    private final FacilityPointRepository facilityPointRepository;
    private final BuildingService buildingService;

    public ExtinguisherService(ExtinguisherRepository extinguisherRepository,
                               BuildingRepository buildingRepository,
                               BuildingFloorRepository buildingFloorRepository,
                               FacilityPointRepository facilityPointRepository,
                               BuildingService buildingService) {
        this.extinguisherRepository = extinguisherRepository;
        this.buildingRepository = buildingRepository;
        this.buildingFloorRepository = buildingFloorRepository;
        this.facilityPointRepository = facilityPointRepository;
        this.buildingService = buildingService;
    }

    @Transactional(readOnly = true)
    public List<Views.ExtinguisherView> list(Long buildingId) {
        List<Extinguisher> cylinders = buildingId == null
                ? extinguisherRepository.findAll(Sort.by("id"))
                : extinguisherRepository.findAllByBuildingId(buildingId, Sort.by("id"));
        Map<Long, Building> buildingMap = new HashMap<>();
        Map<Long, BuildingFloor> floorMap = new HashMap<>();
        Map<Long, FacilityPoint> pointMap = new HashMap<>();
        List<Views.ExtinguisherView> list = new ArrayList<>();
        for (Extinguisher cylinder : cylinders) {
            list.add(toView(cylinder, buildingMap, floorMap, pointMap));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public Views.ExtinguisherSummary summary() {
        LocalDate today = LocalDate.now();
        long total = extinguisherRepository.count();
        long soonExpire = extinguisherRepository.findAll().stream()
                .filter(c -> !"SCRAP".equals(c.getStatus()))
                .filter(c -> daysLeft(c.getHydroTestDue(), today) <= FireDict.HYDRO_WARN_DAYS
                        || daysLeft(c.getScrapDate(), today) <= FireDict.SCRAP_WARN_DAYS)
                .count();
        return new Views.ExtinguisherSummary(
                total,
                extinguisherRepository.countByStatus("NORMAL"),
                extinguisherRepository.countByStatus("REFILL"),
                extinguisherRepository.countByStatus("HYDRO"),
                extinguisherRepository.countByStatus("SCRAP"),
                soonExpire);
    }

    /** 登记新瓶：钢印号唯一，编码与到期日由生命周期回调补齐。 */
    @Transactional
    public Views.ExtinguisherView create(ExtinguisherDtos.CreateReq req) {
        Building building = buildingService.ensureBuilding(req.getBuildingId());
        if (!FireDict.isValidCylinderType(req.getCylType())) {
            throw new BizException("灭火器类型只能是：干粉 / 二氧化碳 / 水基 / 泡沫");
        }
        if (req.getChargeKg() == null || req.getChargeKg().signum() <= 0) {
            throw new BizException("充装量必须大于 0");
        }
        if (extinguisherRepository.findBySteelNo(req.getSteelNo()).isPresent()) {
            throw new BizException("钢印编号已存在：" + req.getSteelNo());
        }
        Extinguisher cylinder = new Extinguisher();
        cylinder.setSteelNo(req.getSteelNo());
        cylinder.setCylType(req.getCylType());
        cylinder.setChargeKg(req.getChargeKg());
        cylinder.setBuildingId(building.getId());
        cylinder.setFloorId(req.getFloorId());
        cylinder.setPointId(req.getPointId());
        cylinder.setLocationDesc(req.getStorePlace());
        cylinder.setProduceDate(req.getProduceDate());
        cylinder.setLastFillDate(req.getLastFillDate());
        cylinder.setHydroTestDue(req.getHydroTestDue());
        cylinder.setScrapDate(req.getScrapDate());
        cylinder.setPlanX(req.getPlanX() == null ? 500 : req.getPlanX());
        cylinder.setPlanY(req.getPlanY() == null ? 500 : req.getPlanY());
        cylinder.setStatus("NORMAL");
        // saveAndFlush：让 @PrePersist 当场跑完，返回的台账编码与月检周期即刻可见
        Extinguisher saved = extinguisherRepository.saveAndFlush(cylinder);
        return toView(saved, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /** 充装：写上次充装日与上次检查日，下次月检日期交给 @PreUpdate 重算。 */
    @Transactional
    public Views.ExtinguisherView refill(Long id) {
        Extinguisher cylinder = ensure(id);
        if ("SCRAP".equals(cylinder.getStatus())) {
            throw new BizException("已报废的气瓶不能再充装");
        }
        LocalDate today = LocalDate.now();
        cylinder.setLastFillDate(today);
        cylinder.setLastCheckDate(today);
        cylinder.setStatus("NORMAL");
        // saveAndFlush：让 @PreUpdate 当场跑完，返回的下次月检日就是重算后的值
        Extinguisher saved = extinguisherRepository.saveAndFlush(cylinder);
        return toView(saved, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /** 送检：标记待水压试验 */
    @Transactional
    public Views.ExtinguisherView markHydro(Long id) {
        Extinguisher cylinder = ensure(id);
        if ("SCRAP".equals(cylinder.getStatus())) {
            throw new BizException("已报废的气瓶不能再送去水压试验");
        }
        cylinder.setStatus("HYDRO");
        // saveAndFlush：让 @PreUpdate 当场跑完，返回的下次月检日就是重算后的值
        Extinguisher saved = extinguisherRepository.saveAndFlush(cylinder);
        return toView(saved, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /** 报废：到期的退役军人416库直接出账 */
    @Transactional
    public Views.ExtinguisherView scrap(Long id) {
        Extinguisher cylinder = ensure(id);
        if ("SCRAP".equals(cylinder.getStatus())) {
            throw new BizException("该气瓶已经是报废状态");
        }
        cylinder.setStatus("SCRAP");
        // saveAndFlush：让 @PreUpdate 当场跑完，返回的下次月检日就是重算后的值
        Extinguisher saved = extinguisherRepository.saveAndFlush(cylinder);
        return toView(saved, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    @Transactional(readOnly = true)
    public Extinguisher ensure(Long id) {
        return extinguisherRepository.findById(id).orElseThrow(() -> new BizException("气瓶不存在：" + id));
    }

    private Views.ExtinguisherView toView(Extinguisher cylinder,
                                          Map<Long, Building> buildingMap,
                                          Map<Long, BuildingFloor> floorMap,
                                          Map<Long, FacilityPoint> pointMap) {
        LocalDate today = LocalDate.now();
        Building building = buildingMap.computeIfAbsent(cylinder.getBuildingId(),
                id -> buildingRepository.findById(id).orElse(null));
        BuildingFloor floor = cylinder.getFloorId() == null ? null
                : floorMap.computeIfAbsent(cylinder.getFloorId(), id -> buildingFloorRepository.findById(id).orElse(null));
        FacilityPoint point = cylinder.getPointId() == null ? null
                : pointMap.computeIfAbsent(cylinder.getPointId(), id -> facilityPointRepository.findById(id).orElse(null));
        long hydroDays = daysLeft(cylinder.getHydroTestDue(), today);
        long scrapDays = daysLeft(cylinder.getScrapDate(), today);
        String risk = riskLevel(cylinder, hydroDays, scrapDays);
        return new Views.ExtinguisherView(
                cylinder.getId(), cylinder.getPointCode(), cylinder.getSteelNo(), cylinder.getCylType(),
                FireDict.cylinderTypeName(cylinder.getCylType()), cylinder.getChargeKg(),
                cylinder.getProduceDate(), cylinder.getLastFillDate(), cylinder.getHydroTestDue(),
                cylinder.getScrapDate(), cylinder.getPointId(),
                point == null ? null : point.getPointCode(),
                cylinder.getBuildingId(), building == null ? "" : building.getName(),
                cylinder.getFloorId(), floor == null ? "" : floor.getFloorCode(),
                cylinder.getLocationDesc(), cylinder.getInstallDate(), cylinder.getCheckCycleDays(),
                cylinder.getLastCheckDate(), cylinder.getNextCheckDate(),
                cylinder.getStatus(), FireDict.cylinderStatusName(cylinder.getStatus()),
                cylinder.getPlanX(), cylinder.getPlanY(), hydroDays, scrapDays, risk);
    }

    /** 到期风险等级：DANGER 已到期 / WARN 临期 / OK */
    private String riskLevel(Extinguisher cylinder, long hydroDays, long scrapDays) {
        if ("SCRAP".equals(cylinder.getStatus())) {
            return "SCRAPPED";
        }
        if (hydroDays <= 0 || scrapDays <= 0) {
            return "DANGER";
        }
        if (hydroDays <= FireDict.HYDRO_WARN_DAYS || scrapDays <= FireDict.SCRAP_WARN_DAYS) {
            return "WARN";
        }
        return "OK";
    }

    private static long daysLeft(LocalDate date, LocalDate today) {
        if (date == null) {
            return 9999L;
        }
        return ChronoUnit.DAYS.between(today, date);
    }
}
