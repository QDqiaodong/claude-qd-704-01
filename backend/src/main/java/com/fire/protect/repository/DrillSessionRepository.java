package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DrillSession;

public interface DrillSessionRepository extends JpaRepository<DrillSession, Long> {

    List<DrillSession> findAllByOrderByIdDesc();

    List<DrillSession> findByStatusInOrderByIdDesc(List<String> statuses);

    /** 后台超时扫描：只锁进行中的场次（暂停场次的倒计时已冻结，不会超时） */
    List<DrillSession> findByStatusOrderByIdAsc(String status);
}
