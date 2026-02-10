package com.example.factory_utility_visualization_be.repository.test;


import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface UtilityOverviewRepository extends JpaRepository<DummyEntity, Long> {

    @Query(value = """
            SELECT
                 d1.id,
                 m.PLC_address AS plcAddress,
                 d1.PLC_value AS plcValue,
                 d1.dateadd,
                 m.Data_Type AS dataType,
                 m.unit_name AS unitName,
                 m.Position AS position,
                 m.Description AS description,
                 m.FullName AS fullName,
                 m.ShortName AS shortName,
                 m.Fac AS fac
             FROM F2Database.dbo.F2_Utility_Master m
             OUTER APPLY (
                 SELECT TOP 1 id, PLC_value, dateadd
                 FROM F2Database.dbo.F2_Utility_Data d
                 WHERE d.PLC_address = m.PLC_address
                 ORDER BY d.dateadd DESC
             ) d1
             WHERE m.Fac IN (:facList)
            
             ORDER BY m.Fac, d1.dateadd DESC;
            
            """, nativeQuery = true)
    List<Object[]> findLatestUtilityDataByFact(@Param("facList") List<String> facList);

    @Query(value = """
                SELECT
                     d.id,
                     m.PLC_address AS plcAddress,
                     d.PLC_value   AS plcValue,
                     d.dateadd,
                     m.Data_Type   AS dataType,
                     m.unit_name   AS unitName,
                     m.Position    AS position,
                     m.Description AS description,
                     m.FullName    AS fullName,
                     m.ShortName   AS shortName,
                     m.Fac         AS fac
                 FROM F2Database.dbo.F2_Utility_Master m
                 JOIN F2Database.dbo.F2_Utility_Data d
                   ON d.PLC_address = m.PLC_address
                 WHERE m.Fac IN (:facList)
                   AND d.dateadd >= :fromDate
                   AND d.dateadd <  :toDate
                 ORDER BY m.Fac, m.PLC_address, d.dateadd DESC
            """, nativeQuery = true)
    List<Object[]> getOverviewInRange(
            @Param("facList") List<String> facList,
            @Param("fromDate") Timestamp fromDate,
            @Param("toDate") Timestamp toDate
    );


}
