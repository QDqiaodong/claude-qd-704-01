package com.fire.protect.domain;

import java.util.Map;

/**
 * 消防演练模块词典：场次 / 指令卡 / 事件类型中文名集中在此。
 */
public final class DrillDict {

    private DrillDict() {
    }

    public static final String DRILL_NO_PREFIX = "DR";

    // ---------------- 场次状态 ----------------
    public static final String SESSION_DRAFT = "DRAFT";
    public static final String SESSION_READY = "READY";
    public static final String SESSION_RUNNING = "RUNNING";
    public static final String SESSION_PAUSED = "PAUSED";
    public static final String SESSION_COMPLETED = "COMPLETED";
    public static final String SESSION_CANCELED = "CANCELED";

    private static final Map<String, String> SESSION_STATUS = Map.of(
            SESSION_DRAFT, "草稿",
            SESSION_READY, "就绪",
            SESSION_RUNNING, "进行中",
            SESSION_PAUSED, "暂停",
            SESSION_COMPLETED, "已完成",
            SESSION_CANCELED, "已取消");

    // ---------------- 指令卡状态 ----------------
    public static final String CARD_PENDING = "PENDING";
    public static final String CARD_WAITING = "WAITING";
    public static final String CARD_CONFIRMED = "CONFIRMED";
    public static final String CARD_TIMEOUT = "TIMEOUT";
    public static final String CARD_SKIPPED = "SKIPPED";

    private static final Map<String, String> CARD_STATUS = Map.of(
            CARD_PENDING, "待下达",
            CARD_WAITING, "等待响应",
            CARD_CONFIRMED, "已确认",
            CARD_TIMEOUT, "已超时",
            CARD_SKIPPED, "已跳过");

    // ---------------- 事件类型 ----------------
    private static final Map<String, String> EVENT_TYPE = Map.ofEntries(
            Map.entry("CREATE", "创建场次"),
            Map.entry("READY", "就绪"),
            Map.entry("START", "开始演练"),
            Map.entry("ISSUE", "下达指令"),
            Map.entry("CONFIRM", "岗位确认"),
            Map.entry("TIMEOUT", "响应超时"),
            Map.entry("PAUSE", "暂停"),
            Map.entry("RESUME", "恢复"),
            Map.entry("SKIP", "跳过节点"),
            Map.entry("CANCEL", "取消演练"),
            Map.entry("FINISH", "结束演练"),
            Map.entry("DISPOSE", "登记处置结论"),
            Map.entry("LATE_ATTEMPT", "迟到确认"),
            Map.entry("SAVE_DRAFT", "保存编排"));

    public static String sessionStatusName(String status) {
        return status == null ? "" : SESSION_STATUS.getOrDefault(status, status);
    }

    public static String cardStatusName(String status) {
        return status == null ? "" : CARD_STATUS.getOrDefault(status, status);
    }

    public static String eventTypeName(String type) {
        return type == null ? "" : EVENT_TYPE.getOrDefault(type, type);
    }

    /** 事件前后状态中文名：场次事件取场次状态名，卡片事件取卡状态名。 */
    public static String cardOrSessionName(String targetType, String status) {
        if (status == null) {
            return null;
        }
        return "CARD".equals(targetType) ? cardStatusName(status) : sessionStatusName(status);
    }
}
