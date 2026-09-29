package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fire.protect.domain.DrillCardDependency;

public interface DrillCardDependencyRepository extends JpaRepository<DrillCardDependency, Long> {

    List<DrillCardDependency> findBySessionIdOrderByIdAsc(Long sessionId);

    List<DrillCardDependency> findByCardIdOrderByIdAsc(Long cardId);

    List<DrillCardDependency> findByPrereqIdOrderByIdAsc(Long prereqId);

    @Modifying
    @Query("delete from DrillCardDependency d where d.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);
}
