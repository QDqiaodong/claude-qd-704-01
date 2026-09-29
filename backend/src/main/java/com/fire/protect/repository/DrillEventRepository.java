package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DrillEvent;

public interface DrillEventRepository extends JpaRepository<DrillEvent, Long> {

    List<DrillEvent> findByDrillIdOrderByIdAsc(Long drillId);
}
