package com.example.factory_utility_visualization_be.service;

import com.example.factory_utility_visualization_be.dto.UtilityCatalogDto;
import com.example.factory_utility_visualization_be.repository.UtilityCatalogRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UtilityCatalogService {

	private final UtilityCatalogRepo repo;

	public UtilityCatalogDto getCatalog(String facId,
	                                    String scadaId,
	                                    String cate,
	                                    String boxDeviceId,
	                                    Boolean importantOnly,
	                                    String include) {

		UtilityCatalogDto out = new UtilityCatalogDto();

		Set<String> inc = parseInclude(include); // null => all

		if (inc.contains("scadas")) {
			out.setScadas(repo.findScadas(nullIfAll(facId)));
		}

		if (inc.contains("channels")) {
			out.setChannels(repo.findChannels(
					nullIfAll(facId),
					emptyToNull(scadaId),
					nullIfAll(cate)
			));
		}

		if (inc.contains("params")) {
			out.setParams(repo.findParams(
					nullIfAll(facId),
					emptyToNull(scadaId),
					nullIfAll(cate),
					emptyToNull(boxDeviceId),
					importantOnly
			));
		}

		if (inc.contains("latest")) {
			out.setLatest(repo.findLatest(
					nullIfAll(facId),
					emptyToNull(scadaId),
					nullIfAll(cate),
					emptyToNull(boxDeviceId)
			));
		}

		return out;
	}

	// ===== helpers =====
	private Set<String> parseInclude(String include) {
		if (include == null || include.trim().isEmpty()) {
			return Set.of("scadas", "channels", "params", "latest");
		}
		String[] parts = include.split(",");
		Set<String> set = new java.util.HashSet<>();
		for (String p : parts) {
			String t = p.trim().toLowerCase();
			if (!t.isEmpty()) set.add(t);
		}
		// safety default
		if (set.isEmpty()) return Set.of("scadas", "channels", "params", "latest");
		return set;
	}

	private String nullIfAll(String s) {
		if (s == null) return null;
		String t = s.trim();
		return (t.isEmpty() || "ALL".equalsIgnoreCase(t)) ? null : t;
	}

	private String emptyToNull(String s) {
		if (s == null) return null;
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
