package com.fire.protect.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 系统操作用户（停用许可闭环里的三类角色）：
 * <ul>
 *   <li>{@code FIRE_MANAGER} 消防主管：提交 / 撤销许可、关闭许可；</li>
 *   <li>{@code SAFETY_OFFICER} 安全负责人：批准 / 拒绝许可；</li>
 *   <li>{@code SITE_OWNER} 现场负责人：确认补偿措施、登记维保单处理说明、发起复位核验。</li>
 * </ul>
 *
 * <p>演示系统不做登录，前端顶栏切换当前操作人，每个请求带 {@code X-User-Id} 头。
 */
@Data
@Entity
@Table(name = "sys_user")
public class SysUser {

    @Id
    @Column(name = "id")
    private Long id;

    /** 登录名（同时作为操作留痕里的操作者） */
    @Column(name = "user_name", nullable = false, length = 32)
    private String userName;

    /** 显示姓名 */
    @Column(name = "display_name", nullable = false, length = 32)
    private String displayName;

    /** FIRE_MANAGER / SAFETY_OFFICER / SITE_OWNER */
    @Column(name = "role", nullable = false, length = 24)
    private String role;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
