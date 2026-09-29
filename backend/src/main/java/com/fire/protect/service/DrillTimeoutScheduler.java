package com.fire.protect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 响应超时兜底扫描器：每秒检查进行中的演练场次，把已到截止时刻仍 WAITING 的卡翻为 TIMEOUT。
 *
 * <p>这是<strong>兜底</strong>而非唯一机制 —— 所有人工动作在读数据前也会惰性判定一次，
 * 所以即使扫描间隔内有点击，超时照样会在服务端被算出来。
 * 扫描只负责「无人操作时自动翻态 + 写超时事件」，不依赖浏览器计时，
 * 服务重启后下一拍扫描照常把漏掉的超时补上（截止时刻已持久化）。
 */
@Component
public class DrillTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(DrillTimeoutScheduler.class);

    private final DrillService drillService;

    public DrillTimeoutScheduler(DrillService drillService) {
        this.drillService = drillService;
    }

    @Scheduled(fixedDelayString = "${drill.timeout.sweep-ms:1000}", initialDelay = 1000)
    public void sweep() {
        try {
            int changed = drillService.sweepTimeouts();
            if (changed > 0) {
                log.info("演练超时扫描：{} 张指令卡到达时限未响应，已翻为已超时", changed);
            }
        } catch (Exception e) {
            // 扫描异常不能把调度线程打死；下一拍重试
            log.warn("演练超时扫描失败（下一拍自动重试）：{}", e.getMessage());
        }
    }
}
