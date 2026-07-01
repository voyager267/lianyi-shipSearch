package com.ripple.planner.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / SpringDoc OpenAPI 配置类。
 * <p>
 * 配置 OpenAPI 文档的基础信息，包括标题、描述、版本、联系人等。
 * Knife4j 基于 SpringDoc OpenAPI，提供增强的 UI 和交互功能。
 * </p>
 * <p>
 * 访问地址：
 * - 增强版 UI: http://127.0.0.1:8081/doc.html
 * - 原生 Swagger UI: http://127.0.0.1:8081/swagger-ui.html
 * - OpenAPI JSON: http://127.0.0.1:8081/v3/api-docs
 * </p>
 */
@Configuration
public class Knife4jConfig {

    /**
     * 配置 OpenAPI 文档基本信息。
     *
     * @return OpenAPI 实例
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ripple Satellite Planner API")
                        .description("基于涟漪模型的动态卫星搜索任务规划系统接口文档。" +
                                "提供任务规划、健康检查等 RESTful API，支持在线调试。")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Ripple Team")
                                .email("support@ripple.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
