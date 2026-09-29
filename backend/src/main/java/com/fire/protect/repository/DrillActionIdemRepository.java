package com.fire.protect.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DrillActionIdem;

public interface DrillActionIdemRepository extends JpaRepository<DrillActionIdem, Long> {

    boolean existsByDrillIdAndClientActionId(Long drillId, String clientActionId);
}
