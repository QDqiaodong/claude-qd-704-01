package com.fire.protect.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fire.protect.domain.DisablePermit;

public interface DisablePermitRepository extends JpaRepository<DisablePermit, Long> {

    List<DisablePermit> findAllByOrderByIdDesc();

    List<DisablePermit> findByStatusOrderByIdDesc(String status);

    List<DisablePermit> findByBuildingIdOrderByIdDesc(Long buildingId);

    List<DisablePermit> findByStatusInOrderByIdDesc(List<String> statuses);

    /** 同一建筑内、区间重叠且仍占用点位的许可（待批准 / 生效 / 待复位），批准时做排他校验 */
    @Query("select p from DisablePermit p where p.buildingId = :buildingId "
            + "and p.status in ('PENDING_APPROVAL','EFFECTIVE','PENDING_RESET') "
            + "and p.planStart <= :planEnd and p.planEnd >= :planStart")
    List<DisablePermit> findOverlapping(@Param("buildingId") Long buildingId,
                                        @Param("planStart") LocalDate planStart,
                                        @Param("planEnd") LocalDate planEnd);

    /** 批量挂起 / 解除许可停用标记，绕过实体生命周期回调（不动原状态、不动检查日） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE facility_point SET active_disable_permit_id = :permitId "
            + "WHERE id IN (:pointIds)", nativeQuery = true)
    int markPoints(@Param("permitId") Long permitId, @Param("pointIds") List<Long> pointIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE facility_point SET active_disable_permit_id = NULL "
            + "WHERE active_disable_permit_id = :permitId", nativeQuery = true)
    int unmarkPoints(@Param("permitId") Long permitId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE disable_permit_point SET marked = :marked WHERE permit_id = :permitId",
            nativeQuery = true)
    int updatePointMarked(@Param("permitId") Long permitId, @Param("marked") boolean marked);
}
