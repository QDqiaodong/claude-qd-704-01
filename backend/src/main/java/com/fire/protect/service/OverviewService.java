package com.fire.protect.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fire.protect.domain.DisablePermit;
import com.fire.protect.domain.Inspection;
import com.fire.protect.dto.Views;
import com.fire.protect.repository.BuildingFloorRepository;
import com.fire.protect.repository.BuildingRepository;
import com.fire.protect.repository.DisablePermitRepository;
import com.fire.protect.repository.ExtinguisherRepository;
import com.fire.protect.repository.FacilityPointRepository;
import com.fire.protect.repository.InspectionRepository;
import com.fire.protect.repository.MaintenanceOrderRepository;

/** 顶栏计数器：平面图、台账、看板共用的整体盘面。 */
@Service
public class OverviewService {

    private final BuildingRepository buildingRepository;
    private final BuildingFloorRepository buildingFloorRepository;
    private final FacilityPointRepository facilityPointRepository;
    private final ExtinguisherRepository extinguisherRepository;
    private final InspectionRepository inspectionRepository;
    private final MaintenanceOrderRepository maintenanceOrderRepository;
    private final DisablePermitRepository disablePermitRepository;

    public OverviewService(BuildingRepository buildingRepository,
                           BuildingFloorRepository buildingFloorRepository,
                           FacilityPointRepository facilityPointRepository,
                           ExtinguisherRepository extinguisherRepository,
                           InspectionRepository inspectionRepository,
                           MaintenanceOrderRepository maintenanceOrderRepository,
                           DisablePermitRepository disablePermitRepository) {
        this.buildingRepository = buildingRepository;
        this.buildingFloorRepository = buildingFloorRepository;
        this.facilityPointRepository = facilityPointRepository;
        this.extinguisherRepository = extinguisherRepository;
        this.inspectionRepository = inspectionRepository;
        this.maintenanceOrderRepository = maintenanceOrderRepository;
        this.disablePermitRepository = disablePermitRepository;
    }

    @Transactional(readOnly = true)
    public Views.OverviewView overview() {
        LocalDate today = LocalDate.now();
        long permitEffective = 0;
        long permitExpired = 0;
        long permitPendingReset = 0;
        for (DisablePermit permit : disablePermitRepository.findAll()) {
            if ("EFFECTIVE".equals(permit.getStatus())) {
                permitEffective++;
                if (permit.getPlanEnd() != null && permit.getPlanEnd().isBefore(today)) {
                    permitExpired++;
                }
            } else if ("PENDING_RESET".equals(permit.getStatus())) {
                permitPendingReset++;
                if (permit.getPlanEnd() != null && permit.getPlanEnd().isBefore(today)) {
                    permitExpired++;
                }
            }
        }
        return new Views.OverviewView(
                buildingRepository.count(),
                buildingFloorRepository.count(),
                facilityPointRepository.count(),
                facilityPointRepository.countByStatus("NORMAL"),
                facilityPointRepository.countByStatus("REPLACE"),
                facilityPointRepository.countByStatus("FAULT"),
                facilityPointRepository.countByStatus("DISABLED"),
                facilityPointRepository.countByNextCheckDateBefore(today),
                extinguisherRepository.count(),
                extinguisherRepository.countByStatus("REFILL"),
                extinguisherRepository.countByStatus("SCRAP"),
                extinguisherRepository.countByHydroTestDueBefore(today.plusDays(120)),
                maintenanceOrderRepository.countByStatus("PENDING"),
                maintenanceOrderRepository.countByStatus("ASSIGNED") + maintenanceOrderRepository.countByStatus("WORKING"),
                maintenanceOrderRepository.countByStatus("DONE") + maintenanceOrderRepository.countByStatus("ACCEPTED"),
                inspectionRepository.countByStatus("PROCESSING"),
                latestInspectDate(),
                permitEffective,
                permitExpired,
                permitPendingReset);
    }

    /** 最近一次巡检日期 */
    private LocalDate latestInspectDate() {
        List<Inspection> inspections = inspectionRepository.findAll();
        LocalDate latest = null;
        for (Inspection inspection : inspections) {
            if (inspection.getInspectDate() == null) {
                continue;
            }
            if (latest == null || inspection.getInspectDate().isAfter(latest)) {
                latest = inspection.getInspectDate();
            }
        }
        return latest;
    }
}
