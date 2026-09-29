package com.fire.protect.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fire.protect.common.ApiResponse;
import com.fire.protect.dto.BuildingDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.service.BuildingService;

import jakarta.validation.Valid;

/** 模块一：建筑与楼层。 */
@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    private final BuildingService buildingService;

    public BuildingController(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    /** 建筑台账：每行自带楼层列表 */
    @GetMapping
    public ApiResponse<List<Views.BuildingView>> list() {
        return ApiResponse.success(buildingService.list());
    }

    /** 某栋楼的楼层标签条数据 */
    @GetMapping("/{id}/floors")
    public ApiResponse<List<Views.FloorView>> floors(@PathVariable Long id) {
        return ApiResponse.success(buildingService.floors(id));
    }

    /** 新增建筑：楼层按层数自动生成 */
    @PostMapping
    public ApiResponse<Views.BuildingView> create(@Valid @RequestBody BuildingDtos.CreateReq req) {
        return ApiResponse.success("建筑已建档，楼层自动生成完成", buildingService.create(req));
    }
}
