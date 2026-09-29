package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DrillResponse;

public interface DrillResponseRepository extends JpaRepository<DrillResponse, Long> {

    List<DrillResponse> findByDrillIdOrderByIdAsc(Long drillId);

    List<DrillResponse> findByCardIdOrderByIdAsc(Long cardId);
}
