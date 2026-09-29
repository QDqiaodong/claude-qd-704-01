package com.fire.protect.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 各列表页 / 平面图统一出口的数据形状（record，Jackson 直接序列化）。 */
public final class Views {

    private Views() {
    }

    /** 楼层（建筑台账展开行 + 平面图楼层标签条） */
    public record FloorView(
            Long id,
            Long buildingId,
            String floorCode,
            String floorName,
            Integer sortNo,
            BigDecimal areaSqm,
            Integer roomCount,
            Integer exitCount,
            long pointCount,
            long abnormalCount
    ) {
    }

    /** 建筑台账行（自带楼层列表） */
    public record BuildingView(
            Long id,
            String code,
            String name,
            String address,
            String buildingType,
            String buildingTypeName,
            Integer floorsAbove,
            Integer floorsBelow,
            BigDecimal areaSqm,
            String fireManager,
            String contactPhone,
            long floorCount,
            long pointCount,
            List<FloorView> floors
    ) {
    }

    /** 设施点位行（平面图上每个气泡就是一条） */
    public record FacilityView(
            Long id,
            String pointCode,
            Long buildingId,
            String buildingName,
            Long floorId,
            String floorCode,
            String floorName,
            String facilityType,
            String facilityTypeName,
            String spec,
            String locationDesc,
            LocalDate installDate,
            Integer checkCycleDays,
            LocalDate lastCheckDate,
            LocalDate nextCheckDate,
            String status,
            String statusName,
            Integer planX,
            Integer planY,
            Long daysToNext,
            boolean overdue,
            Long activeDisablePermitId,
            String activeDisablePermitNo,
            boolean permitDisabled
    ) {
    }

    /** 平面图状态统计 */
    public record PlanSummary(
            long total, long normal, long replace, long fault, long disabled, long overdue,
            long permitDisabled
    ) {
    }

    /** 楼层平面图主视觉的一次性返回包 */
    public record PlanViewDTO(
            Long buildingId,
            String buildingName,
            String buildingTypeName,
            List<FloorView> floors,
            List<FacilityView> points,
            PlanSummary summary
    ) {
    }

    /** 巡检明细行 */
    public record InspectionItemView(
            Long id,
            Long inspectionId,
            Long pointId,
            String pointCode,
            String facilityTypeName,
            String locationDesc,
            String floorCode,
            String result,
            String resultName,
            String problemDesc,
            String disablePermitNo
    ) {
    }

    /** 巡检记录行（自带明细） */
    public record InspectionView(
            Long id,
            String inspectNo,
            Long buildingId,
            String buildingName,
            String inspector,
            LocalDate inspectDate,
            String inspectType,
            String inspectTypeName,
            Integer expectedCount,
            Integer actualCount,
            Integer abnormalCount,
            String status,
            String statusName,
            String remark,
            List<InspectionItemView> items
    ) {
    }

    /** 维保单（看板卡片） */
    public record MaintenanceOrderView(
            Long id,
            String orderNo,
            String orderType,
            String orderTypeName,
            Long pointId,
            String pointCode,
            Long extId,
            String extCode,
            String targetName,
            String buildingName,
            String faultDesc,
            String solution,
            BigDecimal materialFee,
            BigDecimal laborFee,
            BigDecimal totalFee,
            String vendor,
            String ownerPerson,
            LocalDate finishDate,
            String status,
            String statusName,
            String nextStatus,
            String nextStatusName,
            String disablePermitNo
    ) {
    }

    /** 维保三列看板 */
    public record MaintenanceBoardDTO(
            List<MaintenanceOrderView> pending,
            List<MaintenanceOrderView> working,
            List<MaintenanceOrderView> done,
            BigDecimal totalAmount
    ) {
    }

    /** 灭火器 / 气瓶台账行 */
    public record ExtinguisherView(
            Long id,
            String extNo,
            String steelNo,
            String cylType,
            String cylTypeName,
            BigDecimal chargeKg,
            LocalDate produceDate,
            LocalDate lastFillDate,
            LocalDate hydroTestDue,
            LocalDate scrapDate,
            Long pointId,
            String pointCode,
            Long buildingId,
            String buildingName,
            Long floorId,
            String floorCode,
            String storePlace,
            LocalDate installDate,
            Integer checkCycleDays,
            LocalDate lastCheckDate,
            LocalDate nextCheckDate,
            String status,
            String statusName,
            Integer planX,
            Integer planY,
            Long hydroDaysLeft,
            Long scrapDaysLeft,
            String riskLevel
    ) {
    }

    /** 台账页汇总徽章 */
    public record ExtinguisherSummary(
            long total, long normal, long refill, long hydro, long scrap, long soonExpire
    ) {
    }

    /** 顶栏计数器 */
    public record OverviewView(
            long buildingCount,
            long floorCount,
            long pointTotal,
            long pointNormal,
            long pointReplace,
            long pointFault,
            long pointDisabled,
            long overdueCount,
            long extCount,
            long extRefill,
            long extScrap,
            long extSoonExpire,
            long orderPending,
            long orderWorking,
            long orderDone,
            long inspectionProcessing,
            LocalDate lastInspectDate,
            long permitEffective,
            long permitExpired,
            long permitPendingReset
    ) {
    }

    // ==================== 停用许可与复位闭环 ====================

    /** 顶栏身份切换用的用户 */
    public record UserView(Long id, String userName, String displayName, String role, String roleName) {
    }

    /** 许可范围内的点位（带原状态快照 + 最新一轮核验结论） */
    public record PermitPointView(
            Long id,
            Long pointId,
            String pointCode,
            Long floorId,
            String floorCode,
            String facilityType,
            String facilityTypeName,
            String locationDesc,
            String originStatus,
            String originStatusName,
            boolean marked,
            String lastResult,
            String lastResultName,
            String lastFailReason,
            String lastVerifyDesc
    ) {
    }

    /** 补偿措施行 */
    public record MeasureView(
            Long id,
            String measureType,
            String measureTypeName,
            String content,
            String specDesc,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt,
            String confirmedBy,
            String confirmRemark,
            boolean confirmed
    ) {
    }

    /** 复位关联的未完成维保单及处理情况 */
    public record PermitOrderView(
            Long orderId,
            String orderNo,
            String status,
            String statusName,
            String faultDesc,
            String handleNote,
            boolean handled
    ) {
    }

    /** 动作留痕行 */
    public record ActionLogView(
            Long id,
            String action,
            String actionName,
            String fromStatus,
            String fromStatusName,
            String toStatus,
            String toStatusName,
            String operator,
            String operatorRole,
            String operatorRoleName,
            String remark,
            LocalDateTime createdAt
    ) {
    }

    /** 许可列表行（摘要字段，详情走 detail） */
    public record PermitListView(
            Long id,
            String permitNo,
            Long buildingId,
            String buildingName,
            String status,
            String statusName,
            String reason,
            LocalDate planStart,
            LocalDate planEnd,
            Long siteOwnerId,
            String siteOwnerName,
            String createdBy,
            long pointCount,
            long measureCount,
            long measureConfirmedCount,
            long unfinishedOrderCount,
            boolean expired,
            boolean expiringSoon,
            boolean allResetPassed,
            long version,
            LocalDateTime createdAt
    ) {
    }

    /** 许可详情（范围 + 措施 + 维保单 + 核验记录 + 留痕） */
    public record PermitDetailView(
            Long id,
            String permitNo,
            Long buildingId,
            String buildingName,
            String status,
            String statusName,
            String reason,
            LocalDate planStart,
            LocalDate planEnd,
            Long siteOwnerId,
            String siteOwnerName,
            String createdBy,
            boolean scopeLocked,
            String approvedBy,
            LocalDateTime approvedAt,
            String rejectReason,
            String closedBy,
            LocalDateTime closedAt,
            String revokedBy,
            LocalDateTime revokedAt,
            String revokeReason,
            LocalDateTime resetStartedAt,
            LocalDateTime lastVerifyAt,
            String lastVerifyBy,
            String lastVerifyFailReason,
            boolean expired,
            boolean expiringSoon,
            long version,
            LocalDateTime createdAt,
            List<PermitPointView> points,
            List<MeasureView> measures,
            List<PermitOrderView> orders,
            List<ActionLogView> logs
    ) {
    }

    /** 值班页分组：当前生效 / 即将到期 / 已经超期（+ 待复位待办） */
    public record DutyBoardDTO(
            LocalDate today,
            List<PermitListView> effective,
            List<PermitListView> expiringSoon,
            List<PermitListView> expired,
            List<PermitListView> pendingReset,
            long riskTotal
    ) {
    }
}
