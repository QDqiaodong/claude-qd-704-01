package com.fire.protect.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fire.protect.common.ApiResponse;
import com.fire.protect.dto.FacilityDtos;
import com.fire.protect.dto.MaintenanceDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.service.FacilityService;

import jakarta.validation.Valid;

/** 模块二：消防设施点位（平面图上的气泡就是一个点位）。 */
@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    /** 点位列表，可按建筑 / 楼层 / 设施类型过滤 */
    @GetMapping
    public ApiResponse<List<Views.FacilityView>> list(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String type) {
        return ApiResponse.success(facilityService.list(buildingId, floorId, type));
    }

    /** 平面图主视觉：楼层 + 点位 + 状态统计 */
    @GetMapping("/plan")
    public ApiResponse<Views.PlanViewDTO> plan(@RequestParam Long buildingId) {
        return ApiResponse.success(facilityService.plan(buildingId));
    }

    /**
     * 新增点位：点位编码与下次检查日都由 {@code @PrePersist} 生成，
     * 请求里不需要（也不允许）带 pointCode / nextCheckDate。
     */
    @PostMapping
    public ApiResponse<Views.FacilityView> create(@Valid @RequestBody FacilityDtos.CreateReq req) {
        return ApiResponse.success("点位已建档，编码由系统自动生成", facilityService.create(req));
    }

    /** 标记已检：下次检查日由 {@code @PreUpdate} 按上次检查日 + 周期重算 */
    @PostMapping("/{id}/check")
    public ApiResponse<Views.FacilityView> markChecked(@PathVariable Long id) {
        return ApiResponse.success("已标记检查，下次到期日同步刷新", facilityService.markChecked(id, null));
    }

    /** 图上直接报修 */
    @PostMapping("/{id}/report")
    public ApiResponse<Views.MaintenanceOrderView> report(@PathVariable Long id,
                                                          @RequestBody(required = false) MaintenanceDtos.CreateReq req) {
        return ApiResponse.success("维保单已生成", facilityService.report(id, req));
    }
}
