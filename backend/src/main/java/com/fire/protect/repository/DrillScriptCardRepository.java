package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DrillScriptCard;

public interface DrillScriptCardRepository extends JpaRepository<DrillScriptCard, Long> {

    List<DrillScriptCard> findByScriptIdOrderBySeqNoAscIdAsc(Long scriptId);

    void deleteByScriptId(Long scriptId);
}
