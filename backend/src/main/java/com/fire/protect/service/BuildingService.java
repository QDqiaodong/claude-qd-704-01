package com.fire.protect.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fire.protect.common.BizException;
import com.fire.protect.domain.Building;
import com.fire.protect.domain.BuildingFloor;
import com.fire.protect.domain.FacilityPoint;
import com.fire.protect.domain.FireDict;
import com.fire.protect.dto.BuildingDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.repository.BuildingFloorRepository;
import com.fire.protect.repository.BuildingRepository;
import com.fire.protect.repository.FacilityPointRepository;

/** 模块一：建筑与楼层。新建建筑时按地上/地下层数自动生成楼层。 */
@Service
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final BuildingFloorRepository buildingFloorRepository;
    private final FacilityPointRepository facilityPointRepository;

    public BuildingService(BuildingRepository buildingRepository,
                           BuildingFloorRepository buildingFloorRepository,
                           FacilityPointRepository facilityPointRepository) {
        this.buildingRepository = buildingRepository;
        this.buildingFloorRepository = buildingFloorRepository;
        this.facilityPointRepository = facilityPointRepository;
    }

    /** 建筑台账列表：每栋楼自带楼层列表（页面展开行直接用）。 */
    @Transactional(readOnly = true)
    public List<Views.BuildingView> list() {
        List<Building> buildings = buildingRepository.findAllByOrderByCodeAsc();
        List<Views.BuildingView> list = new ArrayList<>();
        for (Building building : buildings) {
            list.add(toView(building, floorsOf(building.getId())));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<Views.FloorView> floors(Long buildingId) {
        ensureBuilding(buildingId);
        return floorsOf(buildingId);
    }

    /** 楼层标签条 + 每个楼层的点位数，平面图切换用。 */
    @Transactional(readOnly = true)
    public List<Views.FloorView> floorsOf(Long buildingId) {
        List<BuildingFloor> floors = buildingFloorRepository.findAllByBuildingId(buildingId,
                Sort.by(Sort.Direction.ASC, "sortNo"));
        Map<Long, long[]> stat = new LinkedHashMap<>();
        for (FacilityPoint point : facilityPointRepository.findAllByBuildingId(buildingId, Sort.by("id"))) {
            long[] cell = stat.computeIfAbsent(point.getFloorId(), key -> new long[2]);
            cell[0] = cell[0] + 1;
            boolean abnormal = "REPLACE".equals(point.getStatus()) || "FAULT".equals(point.getStatus())
                    || point.getActiveDisablePermitId() != null;
            if (abnormal) {
                cell[1] = cell[1] + 1;
            }
        }
        List<Views.FloorView> list = new ArrayList<>();
        for (BuildingFloor floor : floors) {
            long[] cell = stat.getOrDefault(floor.getId(), new long[2]);
            list.add(new Views.FloorView(
                    floor.getId(), floor.getBuildingId(), floor.getFloorCode(), floor.getFloorName(),
                    floor.getSortNo(), floor.getAreaSqm(), floor.getRoomCount(), floor.getExitCount(),
                    cell[0], cell[1]));
        }
        return list;
    }

    /**
     * 新增建筑：校验编号唯一、类型合法，然后按地上/地下层数自动生成楼层，
     * 楼层到此就位，新建点位时才有得选。
     */
    @Transactional
    public Views.BuildingView create(BuildingDtos.CreateReq req) {
        if (buildingRepository.existsByCode(req.getCode())) {
            throw new BizException("建筑编号 " + req.getCode() + " 已存在");
        }
        if (buildingRepository.existsByName(req.getName())) {
            throw new BizException("建筑名称已存在：" + req.getName());
        }
        if (!FireDict.isValidBuildingType(req.getBuildingType())) {
            throw new BizException("建筑类型只能是：住宅 / 商业 / 厂房 / 办公 / 仓储");
        }
        int above = nz(req.getFloorsAbove());
        int below = nz(req.getFloorsBelow());
        if (above + below <= 0) {
            throw new BizException("地上层数与地下层数不能同时为 0");
        }

        LocalDateTime now = LocalDateTime.now();
        Building building = new Building();
        building.setCode(req.getCode());
        building.setName(req.getName());
        building.setAddress(req.getAddress());
        building.setBuildingType(req.getBuildingType());
        building.setFloorsAbove(above);
        building.setFloorsBelow(below);
        building.setAreaSqm(req.getAreaSqm() == null ? BigDecimal.ZERO : req.getAreaSqm());
        building.setFireManager(req.getFireManager());
        building.setContactPhone(req.getContactPhone());
        building.setCreatedAt(now);
        building.setUpdatedAt(now);
        Building saved = buildingRepository.save(building);

        // 楼层按建筑规模自动生成：层面积 = 总面积 / 总层数
        int total = above + below;
        BigDecimal perFloor = building.getAreaSqm().compareTo(BigDecimal.ZERO) > 0
                ? building.getAreaSqm().divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        int sortNo = 0;
        for (int i = below; i >= 1; i--) {
            saveFloor(saved.getId(), "B" + i, "地下" + cnNumber(i) + "层", ++sortNo, perFloor, 3, 2, now);
        }
        for (int i = 1; i <= above; i++) {
            saveFloor(saved.getId(), i + "F", cnNumber(i) + "层", ++sortNo, perFloor, i == 1 ? 4 : 6, i == 1 ? 3 : 2, now);
        }
        return toView(saved, floorsOf(saved.getId()));
    }

    private void saveFloor(Long buildingId, String code, String name, int sortNo, BigDecimal area,
                           int roomCount, int exitCount, LocalDateTime now) {
        BuildingFloor floor = new BuildingFloor();
        floor.setBuildingId(buildingId);
        floor.setFloorCode(code);
        floor.setFloorName(name);
        floor.setSortNo(sortNo);
        floor.setAreaSqm(area);
        floor.setRoomCount(roomCount);
        floor.setExitCount(exitCount);
        floor.setCreatedAt(now);
        buildingFloorRepository.save(floor);
    }

    @Transactional(readOnly = true)
    public Building ensureBuilding(Long buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BizException("建筑不存在：" + buildingId));
    }

    private Views.BuildingView toView(Building building, List<Views.FloorView> floors) {
        long pointCount = facilityPointRepository.countByBuildingId(building.getId());
        return new Views.BuildingView(
                building.getId(), building.getCode(), building.getName(), building.getAddress(),
                building.getBuildingType(), FireDict.buildingTypeName(building.getBuildingType()),
                building.getFloorsAbove(), building.getFloorsBelow(), building.getAreaSqm(),
                building.getFireManager(), building.getContactPhone(),
                floors.size(), pointCount, floors);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    /** 层数转中文，用于楼层名称 */
    static String cnNumber(int value) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (value < 10) {
            return digits[value];
        }
        if (value < 20) {
            return "十" + (value % 10 == 0 ? "" : digits[value % 10]);
        }
        int ten = value / 10;
        int one = value % 10;
        return digits[ten] + "十" + (one == 0 ? "" : digits[one]);
    }
}
