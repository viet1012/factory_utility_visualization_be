package com.example.factory_utility_visualization_be.repository;


import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UtilityDataRepository extends JpaRepository<DummyEntity, Long>  {

    @Query(value = """
        SELECT
            D.id,
            D.PLC_address AS plcAddress,
            D.PLC_value AS plcValue,
            D.dateadd,
            M.Data_Type AS dataType,
            M.unit_name AS unitName,
            M.Position AS position,
            M.Description AS description,
            M.FullName AS fullName,
            M.ShortName AS shortName,
            M.Fac AS fac
        FROM F2Database.dbo.F2_Utility_Data D
        LEFT JOIN F2Database.dbo.F2_Utility_Master M
            ON D.PLC_address = M.PLC_address
        WHERE D.dateadd = (
            SELECT MAX(d2.dateadd)
            FROM F2Database.dbo.F2_Utility_Data d2
            WHERE d2.PLC_address = D.PLC_address
        )
        AND M.Fac IN (:facList)
        ORDER BY M.Fac, D.dateadd DESC
        """, nativeQuery = true)
    List<Object[]> findLatestUtilityDataByFacs(@Param("facList") List<String> facList);
}
