/*
 * EVN daily totals (F2 electric consumption.xlsx, sheets "FY26-<Mon> + Solar1")
 * and the loss calibration of the sub-meter grid.
 *
 * - F2_Utility_Evn_Daily: official kWh read from the EVN main meter (sheet),
 *   written ONLY by POST /api/utility/evn-daily/import (upsert by day, scope).
 * - F2_Utility_Loss_Calibration: append-only; one row per
 *   POST /api/utility/loss-calibration/recompute. A day uses the row with the
 *   highest id whose [effective_from, effective_to] contains it.
 *
 * Neither table touches History_Main / measured data.
 * Run once on F2Database (idempotent: creates only what is missing).
 */

IF OBJECT_ID(N'dbo.F2_Utility_Evn_Daily', N'U') IS NULL
BEGIN
	CREATE TABLE dbo.F2_Utility_Evn_Daily (
		day          DATE           NOT NULL,
		scope        VARCHAR(10)    NOT NULL,
		normal_kwh   DECIMAL(19, 4) NOT NULL,
		high_kwh     DECIMAL(19, 4) NOT NULL,
		low_kwh      DECIMAL(19, 4) NOT NULL,
		total_kwh    DECIMAL(19, 4) NOT NULL,
		-- row 34 "Total Solar" of the sheet, KVH only (NULL for FAC_AB / FAC_C)
		solar_kwh    DECIMAL(19, 4) NULL,
		source_file  NVARCHAR(255)  NOT NULL,
		imported_at  DATETIME2(0)   NOT NULL CONSTRAINT DF_F2_Utility_Evn_Daily_imported_at DEFAULT (SYSDATETIME()),
		imported_by  NVARCHAR(100)  NULL,

		CONSTRAINT PK_F2_Utility_Evn_Daily PRIMARY KEY CLUSTERED (day, scope),
		CONSTRAINT CK_F2_Utility_Evn_Daily_scope CHECK (scope IN ('KVH', 'FAC_AB', 'FAC_C')),
		CONSTRAINT CK_F2_Utility_Evn_Daily_kwh CHECK (
			normal_kwh >= 0 AND high_kwh >= 0 AND low_kwh >= 0 AND total_kwh > 0
			AND (solar_kwh IS NULL OR solar_kwh >= 0)),
		CONSTRAINT CK_F2_Utility_Evn_Daily_solar_scope CHECK (solar_kwh IS NULL OR scope = 'KVH')
	);
END
GO

IF OBJECT_ID(N'dbo.F2_Utility_Loss_Calibration', N'U') IS NULL
BEGIN
	CREATE TABLE dbo.F2_Utility_Loss_Calibration (
		id                 BIGINT IDENTITY(1, 1) NOT NULL,
		scope              VARCHAR(10)    NOT NULL,
		-- added to the measured grid of every hour with grid data
		loss_kwh_per_hour  DECIMAL(19, 4) NOT NULL,
		effective_from     DATE           NOT NULL,
		-- inclusive, NULL = open ended
		effective_to       DATE           NULL,
		-- how the value was computed (formula, sample size, window)
		method             NVARCHAR(200)  NOT NULL,
		created_at         DATETIME2(0)   NOT NULL CONSTRAINT DF_F2_Utility_Loss_Calibration_created_at DEFAULT (SYSDATETIME()),
		created_by         NVARCHAR(100)  NULL,

		CONSTRAINT PK_F2_Utility_Loss_Calibration PRIMARY KEY CLUSTERED (id),
		CONSTRAINT CK_F2_Utility_Loss_Calibration_scope CHECK (scope IN ('KVH', 'FAC_AB', 'FAC_C')),
		CONSTRAINT CK_F2_Utility_Loss_Calibration_range CHECK (effective_to IS NULL OR effective_to >= effective_from)
	);

	CREATE INDEX IX_F2_Utility_Loss_Calibration_scope
		ON dbo.F2_Utility_Loss_Calibration (scope, effective_from, id);
END
GO
