package com.example.factory_utility_visualization_be.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
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
}
