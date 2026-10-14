package com.example.factory_utility_visualization_be.dto.runtime.history;

import java.math.BigDecimal;

/**
 * One bucket. ts is ISO-8601 with offset (+07:00).
 * Empty bucket: min/avg/max/last/delta = null, count = 0.
 * last = value of the latest sample in the bucket (all kinds).
 * delta is only set for COUNTER signals.
 * RAW: COUNTER delta = last(bucket) - last(previous bucket).
 * Snapshot readings (HOURLY_SNAPSHOT / DAILY) use History_Main.cur_value
 * (value when NULL); for COUNTER that is the cumulative counter.
 * HOURLY_SNAPSHOT: avg = last = reading, min = max = null.
 *   ANALOG ts = pick_at hour. COUNTER ts = pick_at hour - 1h, delta =
 *   consumption of [ts, ts + 1h) (History_Main.value, negative -> null),
 *   avg = last = cumulative at ts + 1h.
 * DAILY: one bucket per local day (00:00 +07:00); min/avg/max/last/count
 *   are the day's snapshot stats. ANALOG day = CAST(pick_at AS DATE).
 *   COUNTER day = CAST(pick_at - 1h AS DATE), last = cumulative at the end
 *   of the day, delta = SUM(value > 0), null when no positive row.
 */
public record SignalHistoryPointDto(
		String ts,
		BigDecimal min,
		BigDecimal avg,
		BigDecimal max,
		BigDecimal last,
		long count,
		BigDecimal delta
) {}
