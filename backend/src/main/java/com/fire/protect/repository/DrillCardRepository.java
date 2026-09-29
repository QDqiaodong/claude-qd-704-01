package com.fire.protect.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fire.protect.domain.DrillCard;

public interface DrillCardRepository extends JpaRepository<DrillCard, Long> {

    List<DrillCard> findBySessionIdOrderBySeqNoAscIdAsc(Long sessionId);

    /** 定时扫描：进行中场次里截止时间已到、仍在等待响应的卡 */
    @Query("select c from DrillCard c where c.status = 'WAITING' and c.deadline is not null and c.deadline <= :now")
    List<DrillCard> findTimedOut(@Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from DrillCard c where c.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);
}
