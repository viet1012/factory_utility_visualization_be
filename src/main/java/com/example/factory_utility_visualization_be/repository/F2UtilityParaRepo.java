package com.example.factory_utility_visualization_be.repository;

import com.example.factory_utility_visualization_be.model.F2UtilityPara;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface F2UtilityParaRepo extends JpaRepository<F2UtilityPara, Long> {

    List<F2UtilityPara> findByBoxDeviceId(String boxDeviceId);

    // param + join channel + join scada để filter theo facId/scadaId/cate
    @Query("""
    select p from F2UtilityPara p
      join F2UtilityScadaChannel c on c.boxDeviceId = p.boxDeviceId
      join F2UtilityScada s on s.scadaId = c.scadaId
    where (:boxDeviceId is null or p.boxDeviceId = :boxDeviceId)
      and (:cate is null or c.cate = :cate)
      and (:scadaId is null or s.scadaId = :scadaId)
      and (:facId is null or s.fac = :facId)
  """)
    List<F2UtilityPara> searchParams(
            @Param("boxDeviceId") String boxDeviceId,
            @Param("cate") String cate,
            @Param("scadaId") String scadaId,
            @Param("facId") String facId
    );

    // lọc theo cateId list
    List<F2UtilityPara> findByCateIdIn(List<String> cateIds);
}
