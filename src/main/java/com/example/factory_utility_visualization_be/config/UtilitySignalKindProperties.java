package com.example.factory_utility_visualization_be.config;

import com.example.factory_utility_visualization_be.dto.runtime.history.SignalKind;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps f2_utility_para.cate_id -> signal kind (utility.signal-kind.codes).
 * Keys are matched trim + case-insensitive. In application.yml, keys with
 * '_' must be bracketed ("[E_EneCon]") or Spring strips the underscore.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "utility.signal-kind")
public class UtilitySignalKindProperties {

	private SignalKind defaultKind = SignalKind.ANALOG;

	private Map<String, SignalKind> codes = new HashMap<>();

	/**
	 * (1) cateId mapped in codes; (2) name starting with "Slave" -> STATUS;
	 * (3) defaultKind. cateId must already be null when the para row has no
	 * real category (NULL / blank / '0').
	 */
	public SignalKind resolve(String cateId, String nameEn) {
		if (cateId != null && !cateId.isBlank()) {
			String key = cateId.trim().toUpperCase(Locale.ROOT);

			Optional<SignalKind> mapped = codes.entrySet()
					.stream()
					.filter(entry -> entry.getKey().trim().toUpperCase(Locale.ROOT).equals(key))
					.map(Map.Entry::getValue)
					.findFirst();

			if (mapped.isPresent()) {
				return mapped.get();
			}
		}

		if (nameEn != null && nameEn.trim().toLowerCase(Locale.ROOT).startsWith("slave")) {
			return SignalKind.STATUS;
		}

		return defaultKind;
	}
}
