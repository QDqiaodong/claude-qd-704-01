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
import com.fire.protect.dto.PermitDtos;
import com.fire.protect.dto.Views;
import com.fire.protect.service.PermitService;

import jakarta.validation.Valid;

/**
 * 设施停用许可与复位闭环。
 * 角色：消防主管（创建/提交/撤销/关闭）、安全负责人（批准/拒绝）、现场负责人（措施/复位）。
 */
@RestController
@RequestMapping("/api/permits")
public class PermitController {

    private final PermitService permitService;

    public PermitController(PermitService permitService) {
        this.permitService = permitService;
    }

    /** 顶栏身份切换所需的用户清单 */
    @GetMapping("/users")
    public ApiResponse<List<Views.UserView>> users() {
        return ApiResponse.success(permitService.users());
    }

    /** 许可列表，可按建筑 / 状态过滤 */
    @GetMapping
    public ApiResponse<List<Views.PermitListView>> list(@RequestParam(required = false) Long buildingId,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.success(permitService.list(buildingId, status));
    }

    /** 值班页：当前生效 / 即将到期 / 已超期 / 待复位（只读，任何登录身份可看） */
    @GetMapping("/duty")
    public ApiResponse<Views.DutyBoardDTO> duty() {
        return ApiResponse.success(permitService.dutyBoard());
    }

    @GetMapping("/{id}")
    public ApiResponse<Views.PermitDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(permitService.detail(id));
    }

    @PostMapping
    public ApiResponse<Views.PermitDetailView> create(@Valid @RequestBody PermitDtos.SaveReq req) {
        return ApiResponse.success("停用许可草稿已创建", permitService.create(req));
    }

    @PostMapping("/{id}")
    public ApiResponse<Views.PermitDetailView> update(@PathVariable Long id,
                                                      @Valid @RequestBody PermitDtos.SaveReq req) {
        return ApiResponse.success("许可已更新", permitService.update(id, req));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Views.PermitDetailView> submit(@PathVariable Long id) {
        return ApiResponse.success("已提交安全负责人批准", permitService.submit(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Views.PermitDetailView> approve(@PathVariable Long id) {
        return ApiResponse.success("已批准，许可生效", permitService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Views.PermitDetailView> reject(@PathVariable Long id,
                                                      @Valid @RequestBody PermitDtos.RejectReq req) {
        return ApiResponse.success("已拒绝并退回草稿", permitService.reject(id, req.getReason()));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<Views.PermitDetailView> revoke(@PathVariable Long id,
                                                      @Valid @RequestBody PermitDtos.RevokeReq req) {
        return ApiResponse.success("许可已撤销", permitService.revoke(id, req.getReason()));
    }

    /** 现场负责人逐项确认补偿措施收口 */
    @PostMapping("/{id}/measures/{measureId}/confirm")
    public ApiResponse<Views.PermitDetailView> confirmMeasure(@PathVariable Long id,
                                                              @PathVariable Long measureId,
                                                              @RequestBody(required = false) PermitDtos.MeasureConfirmReq req) {
        String remark = req == null ? null : req.getRemark();
        return ApiResponse.success("补偿措施已确认收口", permitService.confirmMeasure(measureId, remark));
    }

    /** 现场负责人为关联的未完成维保单逐单登记处理说明 */
    @PostMapping("/{id}/orders/{orderId}/waive")
    public ApiResponse<Views.PermitDetailView> waiveOrder(@PathVariable Long id,
                                                          @PathVariable Long orderId,
                                                          @Valid @RequestBody PermitDtos.OrderWaiverReq req) {
        return ApiResponse.success("已登记处理说明", permitService.waiveOrder(id, orderId, req.getHandleNote()));
    }

    /** 发起 / 重新发起复位核验（必须逐个点位给结论） */
    @PostMapping("/{id}/reset-verify")
    public ApiResponse<Views.PermitDetailView> resetVerify(@PathVariable Long id,
                                                           @Valid @RequestBody PermitDtos.ResetVerifyReq req) {
        return ApiResponse.success("复位核验已提交", permitService.resetVerify(id, req));
    }

    /** 核验全部合格后，消防主管关闭许可并解除许可停用标记 */
    @PostMapping("/{id}/close")
    public ApiResponse<Views.PermitDetailView> close(@PathVariable Long id,
                                                     @Valid @RequestBody PermitDtos.CloseReq req) {
        return ApiResponse.success("许可已关闭，点位许可停用标记已解除", permitService.close(id, req));
    }
}
