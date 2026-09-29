package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.PermitActionLog;

public interface PermitActionLogRepository extends JpaRepository<PermitActionLog, Long> {

    List<PermitActionLog> findByPermitIdOrderByIdAsc(Long permitId);
}
