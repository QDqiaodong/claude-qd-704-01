package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fire.protect.domain.DrillTemplatePost;

public interface DrillTemplatePostRepository extends JpaRepository<DrillTemplatePost, Long> {

    List<DrillTemplatePost> findByTemplateIdOrderBySortNoAscIdAsc(Long templateId);

    @Modifying
    @Query("delete from DrillTemplatePost p where p.templateId = :templateId")
    void deleteByTemplateId(@Param("templateId") Long templateId);
}
