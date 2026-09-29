package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fire.protect.domain.DrillTemplateCard;

public interface DrillTemplateCardRepository extends JpaRepository<DrillTemplateCard, Long> {

    List<DrillTemplateCard> findByTemplateIdOrderBySeqNoAscIdAsc(Long templateId);

    @Modifying
    @Query("delete from DrillTemplateCard c where c.templateId = :templateId")
    void deleteByTemplateId(@Param("templateId") Long templateId);
}
