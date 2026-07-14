package com.example.factory_utility_visualization_be.controller.overview.monthly;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryDto;
import com.example.factory_utility_visualization_be.service.overview.monthly.UtilityEnergyMonthlyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityOverviewMonthlyController {

	private final UtilityEnergyMonthlyService service;

	/**
	 * GET bình thường:
	 * ưu tiên lấy từ cache.
	 */
	@GetMapping("/monthly-summary")
	public ResponseEntity<List<MonthlySummaryDto>>
	getMonthlySummary(
			@RequestParam(
					defaultValue = "KVH"
			)
			String facId,

			@RequestParam
			String month
	) {
		return ResponseEntity.ok(
				service.getMonthlySummary(
						facId,
						month
				)
		);
	}

	/**
	 * FE gọi khi user bấm nút refresh.
	 *
	 * Xóa cache đúng FAC + month,
	 * query DB lại và cache kết quả mới.
	 */
	@PostMapping("/monthly-summary/refresh")
	public ResponseEntity<List<MonthlySummaryDto>>
	refreshMonthlySummary(
			@RequestParam(
					defaultValue = "KVH"
			)
			String facId,

			@RequestParam
			String month
	) {
		return ResponseEntity.ok(
				service.refreshMonthlySummary(
						facId,
						month
				)
		);
	}

	/**
	 * Dùng cho admin hoặc debug.
	 * Xóa toàn bộ monthly cache.
	 */
	@DeleteMapping("/monthly-summary/cache")
	public ResponseEntity<Void>
	clearMonthlyCache() {
		service.clearAllMonthlyCache();

		return ResponseEntity
				.noContent()
				.build();
	}
}