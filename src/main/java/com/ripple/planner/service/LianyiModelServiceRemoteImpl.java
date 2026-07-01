package com.ripple.planner.service;

import com.ripple.planner.model.LianyiQueryParam;
import com.ripple.planner.model.LianyiResultNew;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * 涟漪模型服务远程调用实现。
 * <p>
 * 通过 HTTP 调用外部涟漪模型计算服务（lianyiservice），
 * 替代原有的本地 Stub 实现，提供真实的涟漪扩散区域计算能力。
 * </p>
 * <p>
 * 接口契约：
 * - 请求：POST {lianyi.service.url}/lianyi/getTargetLianyiCircle
 * - 请求体：LianyiQueryParam JSON（字段名与远程服务一致，直接序列化发送）
 * - 响应：R&lt;List&lt;LianyiResultNew&gt;&gt;，code=200 且 status=true 表示成功
 * </p>
 * <p>
 * 设计说明：
 * 1. 实现 LianyiModelService 接口，对 Planner 模块透明，调用方无需任何改动。
 * 2. 使用 RestTemplate 进行同步 HTTP 调用，简单可靠，符合当前项目技术栈。
 * 3. 响应包装类 {@link RemoteResponse} 用于映射远程服务的统一返回结构，避免引入外部依赖。
 * 4. 异常处理：远程服务异常、网络异常均包装为 RuntimeException 抛出，
 *    由 RippleTaskPlannerImpl 现有的 try-catch 捕获并终止规划。
 * </p>
 */
@Slf4j
@Service
@Primary
public class LianyiModelServiceRemoteImpl implements LianyiModelService {

    /**
     * 涟漪模型服务基础地址，从配置文件读取。
     */
    @Value("${lianyi.service.url}")
    private String lianyiServiceUrl;

    /**
     * HTTP 客户端，用于调用远程涟漪模型服务。
     */
    private final RestTemplate restTemplate;

    /**
     * 远程接口路径。
     */
    private static final String TARGET_LIANYI_CIRCLE_PATH = "/lianyi/getTargetLianyiCircle";

    /**
     * 构造远程调用实现。
     * <p>
     * Spring Boot 会自动注入 RestTemplate（web starter 已包含）。
     * </p>
     */
    public LianyiModelServiceRemoteImpl() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 执行涟漪模型计算（远程调用）。
     * <p>
     * 将 LianyiQueryParam 序列化为 JSON，发送 POST 请求到远程涟漪模型服务，
     * 解析响应后返回 LianyiResultNew 列表。
     * </p>
     *
     * @param param 涟漪模型查询参数
     * @return 涟漪模型计算结果列表
     * @throws RuntimeException 远程调用失败或服务返回异常状态码时抛出
     */
    @Override
    public List<LianyiResultNew> calculate(LianyiQueryParam param) {
        if (param == null) {
            log.warn("涟漪模型远程服务收到空参数，返回空结果");
            return Collections.emptyList();
        }

        String url = lianyiServiceUrl + TARGET_LIANYI_CIRCLE_PATH;
        log.debug("调用远程涟漪模型服务：url={}, center=({}, {}), entityID={}",
                url, param.getCenterLon(), param.getCenterLat(), param.getEntityID());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LianyiQueryParam> requestEntity = new HttpEntity<>(param, headers);

        try {
            ResponseEntity<RemoteResponse<List<LianyiResultNew>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<>() {}
            );

            RemoteResponse<List<LianyiResultNew>> body = response.getBody();
            if (body == null) {
                throw new RuntimeException("远程涟漪模型服务返回空响应体");
            }

            if (!body.isStatus() || body.getCode() == null || body.getCode() != 200) {
                String msg = body.getMessage() != null ? body.getMessage() : "未知错误";
                throw new RuntimeException("远程涟漪模型服务返回异常：code=" + body.getCode() + ", message=" + msg);
            }

            List<LianyiResultNew> result = body.getData();
            if (result == null) {
                log.warn("远程涟漪模型服务返回 data 为 null，视为空结果");
                return Collections.emptyList();
            }

            log.debug("远程涟漪模型服务调用成功，返回 {} 个结果区域", result.size());
            return result;

        } catch (RestClientResponseException e) {
            log.error("远程涟漪模型服务 HTTP 调用失败：status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("涟漪模型远程调用失败：HTTP " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("远程涟漪模型服务调用异常", e);
            throw new RuntimeException("涟漪模型远程调用异常：" + e.getMessage(), e);
        }
    }

    /**
     * 远程服务统一响应包装类。
     * <p>
     * 与目标项目 com.iecas.lianyi.utils.response.R 结构完全一致，
     * 用于反序列化远程服务的 JSON 响应，避免引入外部依赖。
     * </p>
     *
     * @param <T> 业务数据类型
     */
    @Data
    @NoArgsConstructor
    public static class RemoteResponse<T> {
        /**
         * 状态码，200 表示成功。
         */
        private Integer code;

        /**
         * 返回消息。
         */
        private String message;

        /**
         * 状态，true 表示成功。
         */
        private boolean status;

        /**
         * 业务数据。
         */
        private T data;
    }

}
