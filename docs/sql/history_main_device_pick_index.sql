/* =====================================================================
   Index cho F2_Utility_Para_History_Main phục vụ:
   - ElectricConsumptionRepo.findMeterHourlyEnergy  (HourlyEnergyCache, 1 query / tháng)
   - ElectricConsumptionRepo.findLastEnergyPickAt   (poll mỗi phút, 1 seek / energy para)
   - ElectricConsumptionRepo.findHourlyEnergy / findMeterDays (/electric-consumption)
   - UtilityMonthlyJdbcRepository.envSummary  (water / air của monthly-summary,
     đọc [value] theo (box_device_id, plc_address, pick_at), không cần MTD)
   Mọi query trên join (box_device_id, plc_address) rồi lọc pick_at theo khoảng,
   đọc value, MTD => seek theo (box_device_id, plc_address, pick_at), không lookup.

   CHƯA CHẠY. Repo không có migration / script index nào, nên chưa biết DB đã có
   index tương đương chưa: chạy BƯỚC 1 trước.
   ===================================================================== */

-- BƯỚC 1: index hiện có của bảng (cột key theo thứ tự + cột INCLUDE)
SELECT
    i.name                                   AS index_name,
    i.type_desc,
    i.is_unique,
    STUFF((
        SELECT ', ' + c.name + CASE WHEN ic.is_descending_key = 1 THEN ' DESC' ELSE '' END
        FROM sys.index_columns ic
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
        ORDER BY ic.key_ordinal
        FOR XML PATH('')), 1, 2, '')         AS key_columns,
    STUFF((
        SELECT ', ' + c.name
        FROM sys.index_columns ic
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 1
        FOR XML PATH('')), 1, 2, '')         AS included_columns
FROM sys.indexes i
WHERE i.object_id = OBJECT_ID('dbo.F2_Utility_Para_History_Main')
ORDER BY i.index_id;

-- Đã đủ nếu có index (clustered hoặc nonclustered) với key bắt đầu bằng
-- box_device_id, plc_address, pick_at và (nonclustered) INCLUDE value, MTD
-- (hoặc chính clustered index có key đó). Khi đó KHÔNG cần bước 2.

-- Kích thước bảng (ước lượng thời gian / dung lượng tạo index)
EXEC sp_spaceused 'dbo.F2_Utility_Para_History_Main';

-- BƯỚC 2: tạo index nếu chưa có
-- ONLINE = ON chỉ có ở Enterprise / Developer / Azure SQL; bản Standard bỏ
-- ONLINE = ON và chạy ngoài giờ (bảng bị khóa ghi trong lúc tạo).
IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.F2_Utility_Para_History_Main')
      AND name = 'IX_History_Main_Device_Pick'
)
BEGIN
    CREATE NONCLUSTERED INDEX IX_History_Main_Device_Pick
        ON dbo.F2_Utility_Para_History_Main (box_device_id, plc_address, pick_at)
        INCLUDE ([value], MTD)
        WITH (ONLINE = ON, SORT_IN_TEMPDB = ON, DATA_COMPRESSION = PAGE);
END;

-- CHẨN ĐOÁN tháng đóng chậm hơn tháng hiện tại (summary SQL 7.5-10.6 s vs ~120 ms)
-- Thống kê trên pick_at: ngày cập nhật và mốc cuối của histogram. Nếu mốc cuối
-- cũ hơn đầu tháng hiện tại, khoảng pick_at của tháng hiện tại nằm ngoài
-- histogram -> ước lượng rất ít dòng -> nested loop / seek (nhanh); tháng đã
-- đóng nằm trọn trong histogram -> ước lượng hàng triệu dòng -> có thể chọn
-- scan cả khoảng pick_at của MỌI param rồi hash join (chậm).
SELECT s.name AS stats_name, c.name AS column_name, sp.last_updated, sp.rows, sp.modification_counter
FROM sys.stats s
JOIN sys.stats_columns sc ON sc.object_id = s.object_id AND sc.stats_id = s.stats_id AND sc.stats_column_id = 1
JOIN sys.columns c ON c.object_id = s.object_id AND c.column_id = sc.column_id
CROSS APPLY sys.dm_db_stats_properties(s.object_id, s.stats_id) sp
WHERE s.object_id = OBJECT_ID('dbo.F2_Utility_Para_History_Main');
-- DBCC SHOW_STATISTICS ('dbo.F2_Utility_Para_History_Main', <stats_name trên pick_at>) WITH HISTOGRAM;
-- So sánh actual execution plan (SET STATISTICS IO, TIME ON) của query cũ cho
-- month=202610 và 202611: logical reads trên History_Main + loại join.

-- BƯỚC 3 (sau khi tạo): kiểm tra plan dùng index seek
-- SET STATISTICS IO, TIME ON; rồi chạy query tháng của HourlyEnergyCache
-- (ElectricConsumptionRepo.findMeterHourlyEnergy, :fromTs = đầu tháng + 1h,
-- :toTs = đầu tháng sau + 1h) và xem logical reads trên History_Main.
