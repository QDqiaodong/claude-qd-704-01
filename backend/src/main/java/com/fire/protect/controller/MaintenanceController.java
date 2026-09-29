package com.fire.protect.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fire.protect.common.ApiResponse;
import com.fire.protect.dto.MaintenanceDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.service.MaintenanceService;

import jakarta.validation.Valid;

/** 模块四：维保单（三列看板 + 状态流转）。 */
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public ApiResponse<List<Views.MaintenanceOrderView>> list() {
        return ApiResponse.success(maintenanceService.list());
    }

    /** 三列看板：待派工 / 处理中 / 已完成 */
    @GetMapping("/board")
    public ApiResponse<Views.MaintenanceBoardDTO> board() {
        return ApiResponse.success(maintenanceService.board());
    }

    @PostMapping
    public ApiResponse<Views.MaintenanceOrderView> create(@Valid @RequestBody MaintenanceDtos.CreateReq req) {
        return ApiResponse.success("维保单已登记，当前状态：待派工", maintenanceService.create(req));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<Views.MaintenanceOrderView> assign(@PathVariable Long id,
                                                          @Valid @RequestBody MaintenanceDtos.AssignReq req) {
        return ApiResponse.success("已派工", maintenanceService.assign(id, req));
    }

    /** 状态向前流转：处理中 / 已完成 / 已验收 */
    @PostMapping("/{id}/advance")
    public ApiResponse<Views.MaintenanceOrderView> advance(@PathVariable Long id,
                                                           @RequestBody MaintenanceDtos.StatusReq req) {
        return ApiResponse.success("状态已推进", maintenanceService.advance(id, req));
    }
}
