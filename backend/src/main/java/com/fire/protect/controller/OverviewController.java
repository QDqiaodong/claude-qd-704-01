package com.fire.protect.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fire.protect.common.ApiResponse;
import com.fire.protect.dto.Views;
import com.fire.protect.service.OverviewService;

/** 全局盘面：顶栏计数器的数据源。 */
@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    public ApiResponse<Views.OverviewView> overview() {
        return ApiResponse.success(overviewService.overview());
    }
}
