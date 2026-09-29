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
import com.fire.protect.dto.DrillDtos;
import com.fire.protect.dto.DrillViews;
import com.fire.protect.service.DrillService;

import jakarta.validation.Valid;

/**
 * 消防演练现场指令与响应打点。
 *
 * <p>场次：创建 → 编排保存 → 就绪 → 开始（冻结指令链）→ 暂停/恢复 → 结束 / 取消；
 * 指令卡：下达（前置全部确认才可）→ 等待响应（服务端计时）→ 确认 / 超时 / 跳过。
 * 除查询外，每个动作都带客户端 actionKey，重复发送只回放不重复推进。
 */
@RestController
@RequestMapping("/api/drills")
public class DrillController {

    private final DrillService drillService;

    public DrillController(DrillService drillService) {
        this.drillService = drillService;
    }

    // ==================== 场次 ====================

    @GetMapping
    public ApiResponse<List<DrillViews.DrillListItem>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(drillService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<DrillViews.DrillDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(drillService.detail(id));
    }

    @PostMapping
    public ApiResponse<DrillViews.DrillDetailView> create(@RequestBody(required = false) DrillDtos.SaveDrillReq req) {
        return ApiResponse.success("演练场次草稿已创建", drillService.create(req));
    }

    /** 草稿编排整单保存（开始后冻结，拒绝保存） */
    @PostMapping("/{id}/draft")
    public ApiResponse<DrillViews.DrillDetailView> saveDraft(@PathVariable Long id,
                                                             @Valid @RequestBody DrillDtos.SaveDrillReq req) {
        return ApiResponse.success("编排已保存", drillService.saveDraft(id, req));
    }

    @PostMapping("/{id}/ready")
    public ApiResponse<DrillViews.DrillDetailView> ready(@PathVariable Long id,
                                                         @Valid @RequestBody DrillDtos.KeyedReq req) {
        return ApiResponse.success("演练已就绪，可以开始", drillService.ready(id, req.getActionKey()));
    }

    @PostMapping("/{id}/reopen")
    public ApiResponse<DrillViews.DrillDetailView> reopen(@PathVariable Long id,
                                                          @Valid @RequestBody DrillDtos.KeyedReq req) {
        return ApiResponse.success("已退回草稿", drillService.reopen(id, req.getActionKey()));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<DrillViews.DrillDetailView> start(@PathVariable Long id,
                                                         @Valid @RequestBody DrillDtos.KeyedReq req) {
        return ApiResponse.success("演练开始，指令链已冻结", drillService.start(id, req.getActionKey()));
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<DrillViews.DrillDetailView> pause(@PathVariable Long id,
                                                         @Valid @RequestBody DrillDtos.KeyedReq req) {
        return ApiResponse.success("演练已暂停，剩余时间已冻结", drillService.pause(id, req.getActionKey()));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<DrillViews.DrillDetailView> resume(@PathVariable Long id,
                                                          @Valid @RequestBody DrillDtos.KeyedReq req) {
        return ApiResponse.success("演练已恢复，从剩余时间继续", drillService.resume(id, req.getActionKey()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<DrillViews.DrillDetailView> cancel(@PathVariable Long id,
                                                          @Valid @RequestBody DrillDtos.CancelReq req) {
        return ApiResponse.success("演练已取消", drillService.cancel(id, req));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<DrillViews.DrillDetailView> finish(@PathVariable Long id,
                                                          @Valid @RequestBody DrillDtos.KeyedReq req) {
        return ApiResponse.success("演练已结束", drillService.finish(id, req));
    }

    // ==================== 指令卡 ====================

    @PostMapping("/{id}/cards/{cardId}/issue")
    public ApiResponse<DrillViews.DrillDetailView> issue(@PathVariable("id") Long sessionId,
                                                         @PathVariable Long cardId,
                                                         @Valid @RequestBody DrillDtos.KeyedReq req) {
        return ApiResponse.success("指令已下达", drillService.issueCard(sessionId, cardId, req));
    }

    @PostMapping("/{id}/cards/{cardId}/confirm")
    public ApiResponse<DrillViews.DrillDetailView> confirm(@PathVariable("id") Long sessionId,
                                                           @PathVariable Long cardId,
                                                           @Valid @RequestBody DrillDtos.ConfirmReq req) {
        return ApiResponse.success("响应已记录", drillService.confirmCard(sessionId, cardId, req));
    }

    @PostMapping("/{id}/cards/{cardId}/skip")
    public ApiResponse<DrillViews.DrillDetailView> skip(@PathVariable("id") Long sessionId,
                                                        @PathVariable Long cardId,
                                                        @Valid @RequestBody DrillDtos.SkipReq req) {
        return ApiResponse.success("节点已跳过，下游依赖卡已标注阻塞", drillService.skipCard(sessionId, cardId, req));
    }

    /** 为已超时 / 已跳过的记录填写处置结论（结束前必填） */
    @PostMapping("/{id}/cards/{cardId}/dispose")
    public ApiResponse<DrillViews.DrillDetailView> dispose(@PathVariable("id") Long sessionId,
                                                           @PathVariable Long cardId,
                                                           @Valid @RequestBody DrillDtos.DisposeReq req) {
        return ApiResponse.success("处置结论已保存", drillService.disposeCard(sessionId, cardId, req));
    }

    // ==================== 常用脚本（独立于已开始场次） ====================

    @GetMapping("/templates")
    public ApiResponse<List<DrillViews.TemplateListItem>> templateList() {
        return ApiResponse.success(drillService.templateList());
    }

    @GetMapping("/templates/{templateId}")
    public ApiResponse<DrillViews.TemplateDetailView> templateDetail(@PathVariable Long templateId) {
        return ApiResponse.success(drillService.templateDetail(templateId));
    }

    @PostMapping("/templates")
    public ApiResponse<DrillViews.TemplateDetailView> saveTemplate(@Valid @RequestBody DrillDtos.SaveTemplateReq req) {
        DrillViews.TemplateDetailView view = drillService.saveTemplate(req);
        return ApiResponse.success(req.getId() == null ? "常用脚本已创建" : "常用脚本已更新（不影响已开始的场次）", view);
    }

    @PostMapping("/templates/{templateId}/delete")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long templateId) {
        drillService.deleteTemplate(templateId);
        return ApiResponse.success("脚本已删除", null);
    }
}
