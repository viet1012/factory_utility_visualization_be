package com.example.factory_utility_visualization_be.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.stream.Collectors;

/**
 * TEMPORARY instrumentation (Phase D3 — legacy endpoint retirement support).
 *
 * Logs one structured usage record per request to a legacy Utility API
 * endpoint, so real traffic can be observed before any deprecation or
 * removal decision is made for those endpoints.
 *
 * This interceptor is intentionally minimal and read-only:
 * - it does NOT alter the request, response, status code, or body
 * - it does NOT log parameter values, headers wholesale, cookies, or
 *   authentication material — only parameter NAMES and a small,
 *   explicitly chosen set of low-sensitivity fields
 * - it is restricted, via explicit path registration in WebConfig, to
 *   exactly the 7 legacy endpoints under review; it must not be widened
 *   to match other paths without a fresh review of what gets logged
 *
 * Remove this class (and its registration in WebConfig) once the legacy
 * endpoint retirement decision has been made and the instrumentation is
 * no longer needed.
 */
public class LegacyApiUsageLoggingInterceptor implements HandlerInterceptor {

	/** Dedicated logger category, per Phase D3 requirement: utility.legacy.api.usage */
	private static final Logger LEGACY_USAGE_LOG = LoggerFactory.getLogger("utility.legacy.api.usage");

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
	                             Object handler, Exception ex) {
		if (!LEGACY_USAGE_LOG.isInfoEnabled()) {
			return;
		}

		String path = request.getRequestURI();
		String method = request.getMethod();
		int status = response.getStatus();
		String paramNames = collectParameterNames(request);
		// No trust logic for X-Forwarded-For is introduced here (no reverse-proxy
		// forwarding is configured in this project) — this is the raw socket
		// address as seen by the servlet container.
		String remoteAddr = request.getRemoteAddr();
		String userAgent = safeHeader(request, "User-Agent");
		String referer = safeHeader(request, "Referer");

		// Single-line, key=value structured record — grep/awk/log-pipeline friendly,
		// no new logging/JSON dependency required.
		LEGACY_USAGE_LOG.info(
				"event=legacy_api_usage ts={} path=\"{}\" method={} status={} params=[{}] remoteAddr={} userAgent=\"{}\" referer=\"{}\"",
				Instant.now(),
				path,
				method,
				status,
				paramNames,
				remoteAddr,
				userAgent,
				referer
		);
	}

	private static String collectParameterNames(HttpServletRequest request) {
		Enumeration<String> names = request.getParameterNames();
		if (names == null) {
			return "";
		}
		return Collections.list(names).stream().collect(Collectors.joining(","));
	}

	private static String safeHeader(HttpServletRequest request, String headerName) {
		String value = request.getHeader(headerName);
		return value == null ? "-" : value;
	}
}
