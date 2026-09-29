package com.fire.protect.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.fire.protect.service.DrillScriptService;
import com.fire.protect.service.DrillService;

import jakarta.validation.Valid;

/**
 * 消防演练现场指令与响应打点。
 *
 * <p>三类动作：
 * <ul>
 *   <li>组织者（FIRE_MANAGER）：创建 / 编排 / 就绪 / 开始 / 暂停 / 恢复 / 取消 / 结束 / 下达 / 跳过 / 处置；</li>
 *   <li>岗位人员（岗位绑定用户或 SITE_OWNER）：确认（含迟到确认留痕）；</li>
 *   <li>所有动作 POST 体都可带 {@code clientActionId} 做客户端幂等。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/drills")
public class DrillController {

    private final DrillService drillService;
    private final DrillScriptService scriptService;

    public DrillController(DrillService drillService, DrillScriptService scriptService) {
        this.drillService = drillService;
        this.scriptService = scriptService;
    }

    // ==================== 场次 ====================

    /** 场次列表（可按状态过滤） */
    @GetMapping
    public ApiResponse<List<DrillViews.DrillListView>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(drillService.list(status));
    }

    /** 执行页 / 编排页完整快照（岗位、卡、剩余秒数、当前/下一节点、时间线、结束缺项） */
    @GetMapping("/{id}")
    public ApiResponse<DrillViews.DrillDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(drillService.detail(id));
    }

    /** 轻量时钟（服务端时间 + 场次状态），执行页高频轮询对齐倒计时 */
    @GetMapping("/{id}/clock")
    public ApiResponse<DrillViews.ClockView> clock(@PathVariable Long id) {
        return ApiResponse.success(drillService.clock(id));
    }

    @PostMapping
    public ApiResponse<DrillViews.DrillDetailView> create(@Valid @RequestBody DrillDtos.CreateDrillReq req) {
        return ApiResponse.success("演练场次草稿已创建", drillService.create(req));
    }

    /** 草稿整体保存（岗位 + 指令卡 + 前置节点） */
    @PostMapping("/{id}/draft")
    public ApiResponse<DrillViews.DrillDetailView> saveDraft(@PathVariable Long id,
                                                            @Valid @RequestBody DrillDtos.SaveDraftReq req) {
        return ApiResponse.success("编排已保存", drillService.saveDraft(id, req));
    }

    /** 草稿套用常用脚本（复制为独立指令链） */
    @PostMapping("/{id}/apply-script")
    public ApiResponse<DrillViews.DrillDetailView> applyScript(@PathVariable Long id,
                                                               @Valid @RequestBody DrillDtos.ApplyScriptReq req) {
        return ApiResponse.success("已套用脚本（独立副本）", drillService.applyScript(id, req));
    }

    @PostMapping("/{id}/ready")
    public ApiResponse<DrillViews.DrillDetailView> ready(@PathVariable Long id,
                                                         @RequestBody(required = false) DrillDtos.DrillActionReq req) {
        return ApiResponse.success("场次已就绪，指令链冻结", drillService.ready(id, req));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<DrillViews.DrillDetailView> start(@PathVariable Long id,
                                                         @RequestBody(required = false) DrillDtos.DrillActionReq req) {
        return ApiResponse.success("演练开始", drillService.start(id, req));
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<DrillViews.DrillDetailView> pause(@PathVariable Long id,
                                                         @RequestBody(required = false) DrillDtos.DrillActionReq req) {
        return ApiResponse.success("已暂停，剩余时间冻结", drillService.pause(id, req));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<DrillViews.DrillDetailView> resume(@PathVariable Long id,
                                                          @RequestBody(required = false) DrillDtos.DrillActionReq req) {
        return ApiResponse.success("已恢复，从剩余时间继续", drillService.resume(id, req));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<DrillViews.DrillDetailView> cancel(@PathVariable Long id,
                                                          @Valid @RequestBody DrillDtos.CancelDrillReq req) {
        return ApiResponse.success("演练已取消", drillService.cancel(id, req));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<DrillViews.DrillDetailView> finish(@PathVariable Long id,
                                                          @Valid @RequestBody DrillDtos.FinishDrillReq req) {
        return ApiResponse.success("演练已结束", drillService.finish(id, req));
    }

    // ==================== 指令卡 ====================

    /** 下达指令（前置必须全部已确认，且当前无其他等待响应卡） */
    @PostMapping("/{id}/cards/{cardId}/issue")
    public ApiResponse<DrillViews.DrillDetailView> issue(@PathVariable Long id,
                                                         @PathVariable Long cardId,
                                                         @RequestBody(required = false) DrillDtos.IssueCardReq req) {
        return ApiResponse.success("指令已下达", drillService.issueCard(id, cardId, req));
    }

    /** 岗位确认（实际动作必填、备注选填；终态后到达自动记为迟到尝试） */
    @PostMapping("/{id}/cards/{cardId}/confirm")
    public ApiResponse<DrillViews.DrillDetailView> confirm(@PathVariable Long id,
                                                           @PathVariable Long cardId,
                                                           @Valid @RequestBody DrillDtos.ConfirmCardReq req) {
        return ApiResponse.success("响应已记录", drillService.confirmCard(id, cardId, req));
    }

    /** 跳过节点（原因必填，可选级联跳过被阻塞下游） */
    @PostMapping("/{id}/cards/{cardId}/skip")
    public ApiResponse<DrillViews.DrillDetailView> skip(@PathVariable Long id,
                                                        @PathVariable Long cardId,
                                                        @Valid @RequestBody DrillDtos.SkipCardReq req) {
        return ApiResponse.success("节点已跳过", drillService.skipCard(id, cardId, req));
    }

    /** 为已超时 / 已跳过记录补处置结论 */
    @PostMapping("/{id}/cards/{cardId}/disposition")
    public ApiResponse<DrillViews.DrillDetailView> disposition(@PathVariable Long id,
                                                               @PathVariable Long cardId,
                                                               @Valid @RequestBody DrillDtos.DispositionReq req) {
        return ApiResponse.success("处置结论已登记", drillService.disposeCard(id, cardId, req));
    }

    // ==================== 常用脚本 ====================

    @GetMapping("/scripts")
    public ApiResponse<List<DrillViews.ScriptView>> scripts() {
        return ApiResponse.success(scriptService.list());
    }

    @GetMapping("/scripts/{scriptId}")
    public ApiResponse<DrillViews.ScriptView> scriptDetail(@PathVariable Long scriptId) {
        return ApiResponse.success(scriptService.detail(scriptId));
    }

    @PostMapping("/scripts")
    public ApiResponse<DrillViews.ScriptView> createScript(@Valid @RequestBody DrillDtos.SaveScriptReq req) {
        return ApiResponse.success("脚本已保存", scriptService.create(req));
    }

    @PostMapping("/scripts/{scriptId}")
    public ApiResponse<DrillViews.ScriptView> updateScript(@PathVariable Long scriptId,
                                                           @Valid @RequestBody DrillDtos.SaveScriptReq req) {
        return ApiResponse.success("脚本已更新（不影响已套用该脚本的场次）", scriptService.update(scriptId, req));
    }

    @DeleteMapping("/scripts/{scriptId}")
    public ApiResponse<Void> deleteScript(@PathVariable Long scriptId) {
        scriptService.delete(scriptId);
        return ApiResponse.success("脚本已删除", null);
    }
}
