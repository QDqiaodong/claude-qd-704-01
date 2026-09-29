package com.fire.protect.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

/**
 * 设施停用许可主表。
 *
 * <p>状态机：DRAFT 草稿 → PENDING_APPROVAL 待批准 → EFFECTIVE 生效 → PENDING_RESET 待复位
 * → CLOSED 已关闭；拒绝回到 DRAFT，撤销是旁支（仅草稿 / 待批准可撤销）。
 *
 * <p>{@code version} 乐观锁：同一版本只允许一个动作成功（并发批准 / 撤销 / 复位 / 关闭），
 * 重复请求第二次会撞版本号，不会产生第二条状态记录。
 *
 * <p>{@code scopeLocked}：范围一旦提交即锁定 —— 被拒绝或已生效的许可不能再静默改写
 * 受影响点位；此时只允许改表头说明与补偿措施。
 */
@Data
@Entity
@Table(name = "disable_permit")
public class DisablePermit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 许可编号 DP-年-四位序号 */
    @Column(name = "permit_no", nullable = false, length = 24, updatable = false)
    private String permitNo;

    /** 受影响主建筑（点位可跨楼层，建筑不允许跨） */
    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** 停用原因 */
    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "plan_start", nullable = false)
    private LocalDate planStart;

    @Column(name = "plan_end", nullable = false)
    private LocalDate planEnd;

    /** 现场负责人（必须是 SITE_OWNER 角色用户，复位动作只能由本人发起） */
    @Column(name = "site_owner_id", nullable = false)
    private Long siteOwnerId;

    @Column(name = "site_owner_name", nullable = false, length = 32)
    private String siteOwnerName;

    /** 创建人（消防主管） */
    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    /** DRAFT / PENDING_APPROVAL / EFFECTIVE / PENDING_RESET / CLOSED / REVOKED */
    @Column(name = "status", nullable = false, length = 24)
    private String status;

    /** 范围锁定：首次提交后置 true，之后点位清单不可改 */
    @Column(name = "scope_locked", nullable = false)
    private boolean scopeLocked;

    /** 批准时间（生效起点） */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", length = 32)
    private String approvedBy;

    /** 最近一次拒绝原因 */
    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    /** 关闭时间（许可停用标记在此刻批量解除） */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by", length = 32)
    private String closedBy;

    /** 撤销时间 / 撤销说明 */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by", length = 32)
    private String revokedBy;

    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;

    /** 发起复位核验的时间（进入待复位的时刻） */
    @Column(name = "reset_started_at")
    private LocalDateTime resetStartedAt;

    /** 最近一次复位核验时间（任一点位不合格时保留失败原因） */
    @Column(name = "last_verify_at")
    private LocalDateTime lastVerifyAt;

    @Column(name = "last_verify_by", length = 32)
    private String lastVerifyBy;

    @Column(name = "last_verify_fail_reason", length = 255)
    private String lastVerifyFailReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 乐观锁版本：并发动作只有一个能成功 */
    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
