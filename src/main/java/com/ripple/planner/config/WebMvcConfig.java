package com.ripple.planner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 全局跨域（CORS）配置类。
 * <p>
 * 在非生产环境（dev、test、default 等）下自动启用，允许所有来源跨域访问，
 * 方便前端开发调试和 API 文档（Knife4j/Swagger）在线测试。
 * </p>
 * <p>
 * 生产环境（profile=prod）下不加载此配置，需通过反向代理（Nginx/网关）统一控制跨域策略。
 * </p>
 */
@Configuration
@Profile("!prod")
public class WebMvcConfig {

    /**
     * 配置全局 CORS 规则。
     * <p>
     * 开发环境下允许任意来源、任意方法、任意请求头，并支持携带 Cookie（credentials）。
     * 预检请求（OPTIONS）缓存时间为 1 小时（3600 秒）。
     * </p>
     *
     * @return WebMvcConfigurer 实例
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        // 允许所有来源（开发调试用）
                        .allowedOriginPatterns("*")
                        // 允许所有 HTTP 方法
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        // 允许所有请求头
                        .allowedHeaders("*")
                        // 允许携带凭证（Cookie、Authorization 等）
                        .allowCredentials(true)
                        // 预检请求缓存时间（秒）
                        .maxAge(3600);
            }
        };
    }
}
