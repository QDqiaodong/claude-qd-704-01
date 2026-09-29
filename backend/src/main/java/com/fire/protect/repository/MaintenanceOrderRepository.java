package com.fire.protect.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.MaintenanceOrder;

public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrder, Long> {

    List<MaintenanceOrder> findAllByStatus(String status, Sort sort);

    long countByStatus(String status);

    /** 许可复位前置校验：范围点位上仍处于未完成状态的维保单 */
    List<MaintenanceOrder> findByPointIdInAndStatusIn(Collection<Long> pointIds, Collection<String> statuses);
}
