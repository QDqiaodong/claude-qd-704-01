package com.fire.protect.listener;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fire.protect.domain.BaseFacility;
import com.fire.protect.domain.Building;
import com.fire.protect.domain.Extinguisher;
import com.fire.protect.domain.FacilityPoint;
import com.fire.protect.repository.BuildingRepository;
import com.fire.protect.repository.ExtinguisherRepository;
import com.fire.protect.repository.FacilityPointRepository;

import jakarta.annotation.PostConstruct;

/**
 * JPA 实体监听器（{@code @EntityListeners}）跑在容器之外，拿不到 Spring 注入，
 * 这里用一个静态持有者把「查建筑编号 / 查已有编码序号」的能力透出去。
 * 仅在 {@code @PrePersist} 生成点位编码时被调用。
 */
@Component
public class FacilityLifecycleSupport {

    private static FacilityLifecycleSupport INSTANCE;

    private final BuildingRepository buildingRepository;
    private final FacilityPointRepository facilityPointRepository;
    private final ExtinguisherRepository extinguisherRepository;

    public FacilityLifecycleSupport(BuildingRepository buildingRepository,
                                   FacilityPointRepository facilityPointRepository,
                                   ExtinguisherRepository extinguisherRepository) {
        this.buildingRepository = buildingRepository;
        this.facilityPointRepository = facilityPointRepository;
        this.extinguisherRepository = extinguisherRepository;
    }

    @PostConstruct
    void register() {
        INSTANCE = this;
    }

    static FacilityLifecycleSupport instance() {
        return INSTANCE;
    }

    /** 建筑编号，取不到时用 DEF 兜底 */
    String buildingCode(Long buildingId) {
        if (buildingId == null) {
            return "DEF";
        }
        Building building = buildingRepository.findById(buildingId).orElse(null);
        return building == null ? "DEF" : building.getCode();
    }

    /**
     * 前缀下的下一个四位序号：取同前缀已有编码的最大尾号 + 1（避免多人同时建点时撞号）。
     */
    long nextSequence(BaseFacility facility, String prefix) {
        long max = 0L;
        List<String> codes;
        if (facility instanceof Extinguisher) {
            codes = extinguisherRepository.findByPointCodeStartingWith(prefix).stream()
                    .map(Extinguisher::getPointCode).toList();
        } else {
            codes = facilityPointRepository.findByPointCodeStartingWith(prefix).stream()
                    .map(FacilityPoint::getPointCode).toList();
        }
        for (String code : codes) {
            if (code == null || code.length() < 4) {
                continue;
            }
            try {
                max = Math.max(max, Long.parseLong(code.substring(code.length() - 4)));
            } catch (NumberFormatException ignored) {
                // 编码尾部不是数字（导入的历史数据），跳过不计入序号
            }
        }
        return max + 1L;
    }
}
