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
 * 许可关键动作留痕：每次动作记录操作者、时间、前后状态与说明。
 * 详情页按 id（= 时间顺序）逐条追溯。
 *
 * <p>只在动作真正成功的事务里写入 —— 乐观锁冲突 / 权限不足 / 业务校验失败
 * 都不会产生状态记录，重复提交不会留下两条。
 */
@Data
@Entity
@Table(name = "permit_action_log")
public class PermitActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permit_id", nullable = false)
    private Long permitId;

    /** CREATE / SUBMIT / APPROVE / REJECT / REVOKE / MEASURE_DONE / RESET_VERIFY / CLOSE ... */
    @Column(name = "action", nullable = false, length = 24)
    private String action;

    @Column(name = "from_status", length = 24)
    private String fromStatus;

    @Column(name = "to_status", length = 24)
    private String toStatus;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    /** 操作者角色，便于审计权限是否正确 */
    @Column(name = "operator_role", nullable = false, length = 24)
    private String operatorRole;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
