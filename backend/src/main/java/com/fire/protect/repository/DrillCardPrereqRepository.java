package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import com.fire.protect.domain.DrillCardPrereq;

public interface DrillCardPrereqRepository extends JpaRepository<DrillCardPrereq, Long> {

    List<DrillCardPrereq> findByCardId(Long cardId);

    List<DrillCardPrereq> findByPrereqCardId(Long prereqCardId);

    @Modifying
    @Query("delete from DrillCardPrereq p where p.cardId = :cardId or p.prereqCardId = :cardId")
    void deleteAllRelated(@Param("cardId") Long cardId);
}
