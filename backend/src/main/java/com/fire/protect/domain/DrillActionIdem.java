package com.fire.protect.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 动作幂等键。唯一约束 (drill_id, client_action_id) 兜底 ——
 * 即使两个请求并发穿过服务层的预检，也只有一个事务能插入成功。
 */
@Data
@Entity
@Table(name = "drill_action_idem")
public class DrillActionIdem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drill_id", nullable = false)
    private Long drillId;

    @Column(name = "client_action_id", nullable = false, length = 64)
    private String clientActionId;

    @Column(name = "action", nullable = false, length = 24)
    private String action;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
