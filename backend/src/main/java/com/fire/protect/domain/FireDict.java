package com.fire.protect.domain;

import java.util.List;
import java.util.Map;

/**
 * 消防设施维保词典：类型名/状态名/检查周期/临期阈值等规则集中在此。
 * 服务层只做取值与校验，不散落魔法字符串。
 */
public final class FireDict {

    private FireDict() {
    }

    /** 点位编码前缀（@PrePersist 自动生成 FAC-<建筑编号>-<四位序号>） */
    public static final String FACILITY_CODE_PREFIX = "FAC";
    /** 气瓶台账编码前缀（EXT-<建筑编号>-<四位序号>） */
    public static final String EXTINGUISHER_CODE_PREFIX = "EXT";
    /** 巡检单号前缀 */
    public static final String INSPECTION_NO_PREFIX = "INS";
    /** 维保单号前缀 */
    public static final String ORDER_NO_PREFIX = "MO";
    /** 停用许可单号前缀 */
    public static final String PERMIT_NO_PREFIX = "DP";

    /** 缺省检查周期（天） */
    public static final int DEFAULT_CYCLE_DAYS = 90;
    /** 水压试验到期倒计时预警阈值（天） */
    public static final int HYDRO_WARN_DAYS = 120;
    /** 报废日期倒计时预警阈值（天） */
    public static final int SCRAP_WARN_DAYS = 365;

    // ---------------- 建筑类型 ----------------
    private static final Map<String, String> BUILDING_TYPE = Map.of(
            "RESIDENTIAL", "住宅",
            "COMMERCIAL", "商业",
            "FACTORY", "厂房",
            "OFFICE", "办公",
            "WAREHOUSE", "仓储");

    // ---------------- 设施类型 ----------------
    private static final Map<String, String> FACILITY_TYPE = Map.of(
            "EXTINGUISHER", "灭火器",
            "HYDRANT", "消火栓",
            "SPRINKLER", "喷淋头",
            "SMOKE", "烟感探测器",
            "ALARM", "手动报警按钮",
            "LIGHT", "应急照明",
            "DOOR", "防火门",
            "PUMP", "消防泵");

    /** 各类设施的巡检周期（天）：灭火器/消防泵按月，管类按季，喷淋头按半年 */
    private static final Map<String, Integer> FACILITY_CYCLE = Map.of(
            "EXTINGUISHER", 30,
            "PUMP", 30,
            "HYDRANT", 90,
            "SMOKE", 90,
            "ALARM", 90,
            "LIGHT", 90,
            "DOOR", 90,
            "SPRINKLER", 180);

    // ---------------- 点位状态 ----------------
    private static final Map<String, String> POINT_STATUS = Map.of(
            "NORMAL", "正常",
            "REPLACE", "待更换",
            "FAULT", "故障",
            "DISABLED", "停用");

    // ---------------- 灭火器 / 气瓶状态 ----------------
    private static final Map<String, String> CYLINDER_STATUS = Map.of(
            "NORMAL", "正常",
            "REFILL", "待充装",
            "HYDRO", "待水压",
            "SCRAP", "报废");

    private static final Map<String, String> CYLINDER_TYPE = Map.of(
            "POWDER", "干粉",
            "CO2", "二氧化碳",
            "WATER", "水基",
            "FOAM", "泡沫");

    // ---------------- 巡检 ----------------
    private static final Map<String, String> INSPECTION_TYPE = Map.of(
            "DAILY", "日巡",
            "MONTHLY", "月检",
            "QUARTERLY", "季检",
            "YEARLY", "年检");

    private static final Map<String, String> INSPECTION_STATUS = Map.of(
            "PROCESSING", "进行中",
            "COMPLETED", "已完成");

    private static final Map<String, String> ITEM_RESULT = Map.of(
            "PENDING", "未检",
            "NORMAL", "正常",
            "ABNORMAL", "异常");

    // ---------------- 维保工单 ----------------
    private static final Map<String, String> ORDER_TYPE = Map.of(
            "REPAIR", "维修",
            "REPLACE", "更换",
            "REFILL", "充装",
            "TEST", "检测");

    public static final List<String> ORDER_STATUS_ORDER = List.of(
            "PENDING", "ASSIGNED", "WORKING", "DONE", "ACCEPTED");

    private static final Map<String, String> ORDER_STATUS = Map.of(
            "PENDING", "待派工",
            "ASSIGNED", "已派工",
            "WORKING", "处理中",
            "DONE", "已完成",
            "ACCEPTED", "已验收");

    // ---------------- 停用许可 ----------------
    /** 许可生命周期：草稿 → 待批准 → 生效 → 待复位 → 已关闭（旁支：已撤销；拒绝回到草稿） */
    public static final List<String> PERMIT_STATUS_ORDER = List.of(
            "DRAFT", "PENDING_APPROVAL", "EFFECTIVE", "PENDING_RESET", "CLOSED", "REVOKED");

    private static final Map<String, String> PERMIT_STATUS = Map.of(
            "DRAFT", "草稿",
            "PENDING_APPROVAL", "待批准",
            "EFFECTIVE", "生效",
            "PENDING_RESET", "待复位",
            "CLOSED", "已关闭",
            "REVOKED", "已撤销");

    private static final Map<String, String> PERMIT_ACTION = Map.ofEntries(
            Map.entry("CREATE", "创建草稿"),
            Map.entry("UPDATE", "编辑许可"),
            Map.entry("SUBMIT", "提交批准"),
            Map.entry("APPROVE", "批准生效"),
            Map.entry("REJECT", "拒绝退回"),
            Map.entry("REVOKE", "撤销许可"),
            Map.entry("MEASURE_DONE", "确认补偿措施"),
            Map.entry("ORDER_WAIVER", "登记维保处理说明"),
            Map.entry("RESET_START", "发起复位核验"),
            Map.entry("RESET_VERIFY", "复位核验"),
            Map.entry("CLOSE", "关闭许可"));

    /** 补偿措施类别：替代灭火器 / 加密巡查 / 临时警戒 / 其它 */
    private static final Map<String, String> MEASURE_TYPE = Map.of(
            "SPARE_EXT", "替代灭火器",
            "EXTRA_PATROL", "加密巡查",
            "TEMP_BARRIER", "临时警戒",
            "OTHER", "其它措施");

    private static final Map<String, String> USER_ROLE = Map.of(
            "FIRE_MANAGER", "消防主管",
            "SAFETY_OFFICER", "安全负责人",
            "SITE_OWNER", "现场负责人");

    public static boolean isValidPermitStatus(String status) {
        return status != null && PERMIT_STATUS.containsKey(status);
    }

    public static String permitStatusName(String status) {
        return status == null ? "" : PERMIT_STATUS.getOrDefault(status, status);
    }

    public static String permitActionName(String action) {
        return action == null ? "" : PERMIT_ACTION.getOrDefault(action, action);
    }

    public static boolean isValidMeasureType(String type) {
        return type != null && MEASURE_TYPE.containsKey(type);
    }

    public static String measureTypeName(String type) {
        return type == null ? "" : MEASURE_TYPE.getOrDefault(type, type);
    }

    public static String roleName(String role) {
        return role == null ? "" : USER_ROLE.getOrDefault(role, role);
    }

    // ---------------- 取值 ----------------

    public static boolean isValidBuildingType(String type) {
        return type != null && BUILDING_TYPE.containsKey(type);
    }

    public static String buildingTypeName(String type) {
        return type == null ? "" : BUILDING_TYPE.getOrDefault(type, type);
    }

    public static boolean isValidFacilityType(String type) {
        return type != null && FACILITY_TYPE.containsKey(type);
    }

    public static String facilityTypeName(String type) {
        return type == null ? "" : FACILITY_TYPE.getOrDefault(type, type);
    }

    /** 设施类型对应的巡检周期（天） */
    public static int cycleDays(String type) {
        return FACILITY_CYCLE.getOrDefault(type, DEFAULT_CYCLE_DAYS);
    }

    public static boolean isValidPointStatus(String status) {
        return status != null && POINT_STATUS.containsKey(status);
    }

    public static String pointStatusName(String status) {
        return status == null ? "" : POINT_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidCylinderStatus(String status) {
        return status != null && CYLINDER_STATUS.containsKey(status);
    }

    public static String cylinderStatusName(String status) {
        return status == null ? "" : CYLINDER_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidCylinderType(String type) {
        return type != null && CYLINDER_TYPE.containsKey(type);
    }

    public static String cylinderTypeName(String type) {
        return type == null ? "" : CYLINDER_TYPE.getOrDefault(type, type);
    }

    public static boolean isValidInspectionType(String type) {
        return type != null && INSPECTION_TYPE.containsKey(type);
    }

    public static String inspectionTypeName(String type) {
        return type == null ? "" : INSPECTION_TYPE.getOrDefault(type, type);
    }

    public static boolean isValidInspectionStatus(String status) {
        return status != null && INSPECTION_STATUS.containsKey(status);
    }

    public static String inspectionStatusName(String status) {
        return status == null ? "" : INSPECTION_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidItemResult(String result) {
        return result != null && ITEM_RESULT.containsKey(result);
    }

    public static String itemResultName(String result) {
        return result == null ? "" : ITEM_RESULT.getOrDefault(result, result);
    }

    public static boolean isValidOrderType(String type) {
        return type != null && ORDER_TYPE.containsKey(type);
    }

    public static String orderTypeName(String type) {
        return type == null ? "" : ORDER_TYPE.getOrDefault(type, type);
    }

    public static boolean isValidOrderStatus(String status) {
        return status != null && ORDER_STATUS.containsKey(status);
    }

    public static String orderStatusName(String status) {
        return status == null ? "" : ORDER_STATUS.getOrDefault(status, status);
    }

    /** 维保单状态的下一步；已是最后一档返回 null */
    public static String nextOrderStatus(String status) {
        int index = ORDER_STATUS_ORDER.indexOf(status);
        if (index < 0 || index >= ORDER_STATUS_ORDER.size() - 1) {
            return null;
        }
        return ORDER_STATUS_ORDER.get(index + 1);
    }
}
