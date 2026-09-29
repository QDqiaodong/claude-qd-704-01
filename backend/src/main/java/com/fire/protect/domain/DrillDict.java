package com.fire.protect.domain;

import java.util.List;
import java.util.Map;

/**
 * 消防演练词典：场次 / 指令卡状态名、事件动作名集中在此，服务层不散落魔法字符串。
 */
public final class DrillDict {

    private DrillDict() {
    }

    /** 演练编号前缀 DR-年-四位序号 */
    public static final String DRILL_NO_PREFIX = "DR";

    // ---------------- 场次状态 ----------------
    public static final String SESSION_DRAFT = "DRAFT";
    public static final String SESSION_READY = "READY";
    public static final String SESSION_RUNNING = "RUNNING";
    public static final String SESSION_PAUSED = "PAUSED";
    public static final String SESSION_FINISHED = "FINISHED";
    public static final String SESSION_CANCELED = "CANCELED";

    private static final Map<String, String> SESSION_STATUS = Map.of(
            SESSION_DRAFT, "草稿",
            SESSION_READY, "就绪",
            SESSION_RUNNING, "进行中",
            SESSION_PAUSED, "暂停",
            SESSION_FINISHED, "已完成",
            SESSION_CANCELED, "已取消");

    /** 编排期（可改指令链） */
    public static final List<String> SESSION_EDITABLE = List.of(SESSION_DRAFT);
    /** 可开始 */
    public static final List<String> SESSION_STARTABLE = List.of(SESSION_READY);
    /** 可暂停（暂停时仍有等待中的卡要冻结） */
    public static final List<String> SESSION_PAUSABLE = List.of(SESSION_RUNNING);
    /** 可恢复 */
    public static final List<String> SESSION_RESUMABLE = List.of(SESSION_PAUSED);
    /** 可取消 */
    public static final List<String> SESSION_CANCELABLE =
            List.of(SESSION_DRAFT, SESSION_READY, SESSION_RUNNING, SESSION_PAUSED);
    /** 可结束 */
    public static final List<String> SESSION_FINISHABLE = List.of(SESSION_RUNNING, SESSION_PAUSED);

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

    /** 指令卡终态（终态不再被状态推进改写） */
    public static final List<String> CARD_TERMINAL =
            List.of(CARD_CONFIRMED, CARD_TIMEOUT, CARD_SKIPPED);

    // ---------------- 事件动作 ----------------
    private static final Map<String, String> ACTION_NAME = Map.ofEntries(
            Map.entry("CREATE", "创建演练"),
            Map.entry("READY", "编排就绪"),
            Map.entry("REOPEN", "退回草稿"),
            Map.entry("START", "开始演练"),
            Map.entry("ISSUE", "下达指令"),
            Map.entry("CONFIRM", "岗位确认"),
            Map.entry("LATE_CONFIRM", "迟到确认"),
            Map.entry("TIMEOUT", "系统判定超时"),
            Map.entry("PAUSE", "暂停演练"),
            Map.entry("RESUME", "恢复演练"),
            Map.entry("SKIP", "跳过节点"),
            Map.entry("DISPOSE", "填写处置结论"),
            Map.entry("CANCEL", "取消演练"),
            Map.entry("FINISH", "结束演练"));

    // ---------------- 响应尝试结果 ----------------
    public static final String ATTEMPT_CONFIRMED = "CONFIRMED";
    public static final String ATTEMPT_LATE = "LATE";

    private static final Map<String, String> ATTEMPT_RESULT = Map.of(
            ATTEMPT_CONFIRMED, "按时确认",
            ATTEMPT_LATE, "迟到尝试");

    // ---------------- 取值方法 ----------------

    public static String sessionStatusName(String status) {
        return status == null ? "" : SESSION_STATUS.getOrDefault(status, status);
    }

    public static String cardStatusName(String status) {
        return status == null ? "" : CARD_STATUS.getOrDefault(status, status);
    }

    /** 事件里的前后状态既可能是场次状态也可能是卡状态，统一取名 */
    public static String anyStatusName(String status) {
        if (status == null) {
            return "";
        }
        String name = SESSION_STATUS.get(status);
        if (name != null) {
            return name;
        }
        return CARD_STATUS.getOrDefault(status, status);
    }

    public static String actionName(String action) {
        return action == null ? "" : ACTION_NAME.getOrDefault(action, action);
    }

    public static String attemptResultName(String result) {
        return result == null ? "" : ATTEMPT_RESULT.getOrDefault(result, result);
    }

    public static boolean isCardTerminal(String status) {
        return status != null && CARD_TERMINAL.contains(status);
    }
}
