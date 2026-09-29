package com.fire.protect.repository;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.fire.protect.domain.DrillSession;

import jakarta.persistence.QueryHint;

public interface DrillSessionRepository extends JpaRepository<DrillSession, Long> {

    List<DrillSession> findAllByOrderByIdDesc();

    List<DrillSession> findByStatusOrderByIdDesc(String status);

    /** 动作事务内加行级写锁：多个执行页面同时操作时串行化，状态与事件顺序不打架 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from DrillSession s where s.id = :id")
    DrillSession lockById(@Param("id") Long id);
}
