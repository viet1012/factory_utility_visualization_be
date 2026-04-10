package com.example.factory_utility_visualization_be.repository;

import com.example.factory_utility_visualization_be.model.F2UtilityPara;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface F2UtilityParaRepo extends JpaRepository<F2UtilityPara, Long> {

    List<F2UtilityPara> findByBoxDeviceId(String boxDeviceId);

    List<F2UtilityPara> findByCateId(String cateId);

    List<F2UtilityPara> findByIsImportant(Integer isImportant);

    List<F2UtilityPara> findByIsAlert(Integer isAlert);

    @Query("""
        select p
        from F2UtilityPara p
        where (:boxDeviceId is null or p.boxDeviceId = :boxDeviceId)
          and (:importantOnly = 0 or p.isImportant = 1)
          and exists (
                select 1
                from F2UtilityScadaChannel c, F2UtilityScada s
                where c.boxDeviceId = p.boxDeviceId
                  and s.scadaId = c.scadaId
                  and (:cate is null or c.cate = :cate)
                  and (:scadaId is null or s.scadaId = :scadaId)
                  and (:facId is null or s.fac = :facId)
          )
    """)
    List<F2UtilityPara> searchParams(
            @Param("boxDeviceId") String boxDeviceId,
            @Param("cate") String cate,
            @Param("scadaId") String scadaId,
            @Param("facId") String facId,
            @Param("importantOnly") int importantOnly
    );

    List<F2UtilityPara> findByCateIdIn(List<String> cateIds);
}