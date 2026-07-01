package com.ripple.planner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 应用程序入口。
 * <p>
 * 基于涟漪模型的动态卫星搜索任务规划系统启动类。
 * 使用 @SpringBootApplication 组合注解，启用自动配置、组件扫描和配置属性支持。
 * </p>
 * <p>
 * 启动方式：
 * 1. 在 IDEA 中直接运行 main 方法。
 * 2. 命令行执行：mvn spring-boot:run
 * 3. 打包后执行：java -jar ripple-satellite-planner-1.0.0-SNAPSHOT.jar
 * </p>
 */
@SpringBootApplication
public class RippleSatellitePlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RippleSatellitePlannerApplication.class, args);
    }

}
