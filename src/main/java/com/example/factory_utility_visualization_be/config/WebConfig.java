package com.example.factory_utility_visualization_be.config;


import com.example.factory_utility_visualization_be.interceptor.LegacyApiUsageLoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")  // Cấu hình CORS cho tất cả các endpoint
                .allowedOrigins("*")  // Thêm http://localhost:54483
                .allowedMethods("GET", "POST", "PUT", "DELETE")  // Các phương thức HTTP được phép
                .allowedHeaders("*")  // Các headers được phép
                .allowCredentials(true);  // Cho phép gửi cookie trong yêu cầu
    }

    // TEMPORARY (Phase D3): usage instrumentation for the 7 legacy Utility API
    // endpoints under retirement review. Restricted to exact literal paths only —
    // do not widen this pattern list without re-reviewing what gets logged.
    // Remove this registration together with LegacyApiUsageLoggingInterceptor
    // once the legacy endpoint retirement decision has been made.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LegacyApiUsageLoggingInterceptor())
                .addPathPatterns(
                        "/api/utility/scadas",
                        "/api/utility/channels",
                        "/api/utility/params",
                        "/api/utility/lates1t",
                        "/api/utility/series/hourly",
                        "/api/utility/catalog",
                        "/api/utility/monthly-usage"
                );
    }
}
