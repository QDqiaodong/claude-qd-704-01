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
import com.fire.protect.dto.InspectionDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.service.InspectionService;

import jakarta.validation.Valid;

/** 模块三：巡检记录 + 明细行。 */
@RestController
@RequestMapping("/api/inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping
    public ApiResponse<List<Views.InspectionView>> list(@RequestParam(required = false) Long buildingId) {
        return ApiResponse.success(inspectionService.list(buildingId));
    }

    @GetMapping("/{id}/items")
    public ApiResponse<List<Views.InspectionItemView>> items(@PathVariable Long id) {
        return ApiResponse.success(inspectionService.itemsOf(id));
    }

    /** 新建巡检：按范围自动生成明细行 */
    @PostMapping
    public ApiResponse<Views.InspectionView> create(@Valid @RequestBody InspectionDtos.CreateReq req) {
        return ApiResponse.success("巡检单已生成，明细行按在册点位铺开", inspectionService.create(req));
    }

    @PostMapping("/{id}/items/{itemId}")
    public ApiResponse<Views.InspectionView> saveItem(@PathVariable Long id,
                                                      @PathVariable Long itemId,
                                                      @Valid @RequestBody InspectionDtos.ItemResultReq req) {
        return ApiResponse.success(inspectionService.saveItem(id, itemId, req));
    }

    /** 完成巡检：刷新每个点位的下次检查日，异常点位自动开维修单 */
    @PostMapping("/{id}/complete")
    public ApiResponse<Views.InspectionView> complete(@PathVariable Long id) {
        return ApiResponse.success("巡检已收单，到期日已重算", inspectionService.complete(id));
    }
}
