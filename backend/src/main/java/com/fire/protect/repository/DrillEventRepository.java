package com.fire.protect.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DrillEvent;

public interface DrillEventRepository extends JpaRepository<DrillEvent, Long> {

    List<DrillEvent> findBySessionIdOrderByIdAsc(Long sessionId);

    /** 幂等：同一动作标识在同一场次内只允许一条事件 */
    Optional<DrillEvent> findBySessionIdAndActionKey(Long sessionId, String actionKey);
}
