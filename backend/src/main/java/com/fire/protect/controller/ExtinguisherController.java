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
import com.fire.protect.dto.ExtinguisherDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.service.ExtinguisherService;

import jakarta.validation.Valid;

/** 模块五：灭火器与气瓶台账。 */
@RestController
@RequestMapping("/api/extinguishers")
public class ExtinguisherController {

    private final ExtinguisherService extinguisherService;

    public ExtinguisherController(ExtinguisherService extinguisherService) {
        this.extinguisherService = extinguisherService;
    }

    @GetMapping
    public ApiResponse<List<Views.ExtinguisherView>> list(@RequestParam(required = false) Long buildingId) {
        return ApiResponse.success(extinguisherService.list(buildingId));
    }

    /** 台账页顶部的临期徽章统计 */
    @GetMapping("/summary")
    public ApiResponse<Views.ExtinguisherSummary> summary() {
        return ApiResponse.success(extinguisherService.summary());
    }

    @PostMapping
    public ApiResponse<Views.ExtinguisherView> create(@Valid @RequestBody ExtinguisherDtos.CreateReq req) {
        return ApiResponse.success("气瓶已登记，台账编码与月检到期日已自动生成", extinguisherService.create(req));
    }

    /** 充装：下次月检日期按今天 + 周期重算（@PreUpdate） */
    @PostMapping("/{id}/refill")
    public ApiResponse<Views.ExtinguisherView> refill(@PathVariable Long id) {
        return ApiResponse.success("充装完成，下次月检日期已重算", extinguisherService.refill(id));
    }

    @PostMapping("/{id}/hydro")
    public ApiResponse<Views.ExtinguisherView> markHydro(@PathVariable Long id) {
        return ApiResponse.success("已标记待水压试验", extinguisherService.markHydro(id));
    }

    @PostMapping("/{id}/scrap")
    public ApiResponse<Views.ExtinguisherView> scrap(@PathVariable Long id) {
        return ApiResponse.success("气瓶已报废", extinguisherService.scrap(id));
    }
}
