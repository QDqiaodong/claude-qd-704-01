package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fire.protect.domain.DrillCard;

public interface DrillCardRepository extends JpaRepository<DrillCard, Long> {

    List<DrillCard> findByDrillIdOrderBySeqNoAsc(Long drillId);

    List<DrillCard> findByDrillIdAndStatus(Long drillId, String status);

    /** 后台超时扫描：进行中场次里已到截止时刻、仍在等待响应的卡 */
    @Query("select c from DrillCard c where c.drillId in :drillIds and c.status = 'WAITING' "
            + "and c.deadlineAt <= :now order by c.deadlineAt asc")
    List<DrillCard> findExpiredWaiting(@Param("drillIds") List<Long> drillIds,
                                       @Param("now") java.time.LocalDateTime now);

    @Modifying
    @Query("delete from DrillCardPrereq p where p.cardId in "
            + "(select c.id from DrillCard c where c.drillId = :drillId)")
    void deletePrereqsByDrillId(@Param("drillId") Long drillId);
}
