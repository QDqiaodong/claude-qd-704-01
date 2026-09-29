package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.InspectionItem;

public interface InspectionItemRepository extends JpaRepository<InspectionItem, Long> {

    List<InspectionItem> findAllByInspectionIdOrderByIdAsc(Long inspectionId);

    void deleteByInspectionId(Long inspectionId);
}
