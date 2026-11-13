package com.health.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.health.api.ApiException;
import com.health.mapper.SystemSettingsMapper;
import com.health.model.SystemSettings;
import com.health.service.SystemSettingsService;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
public class SystemSettingsServiceImpl implements SystemSettingsService {
  private final SystemSettingsMapper mapper;
  private final RestClient agent;

  public SystemSettingsServiceImpl(SystemSettingsMapper mapper,
      @Value("${app.agent-url}") String url, @Value("${app.internal-service-key}") String key) {
    this.mapper = mapper;
    var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(95));
    agent = RestClient.builder().baseUrl(url).defaultHeader("X-Service-Key", key)
        .requestFactory(factory).build();
  }

  public SystemSettings get() { return mapper.get(); }

  public SystemSettings save(SystemSettings settings) {
    mapper.save(settings);
    return settings;
  }

  public Map<String, Object> checkConnection(String modelName, int timeoutSeconds) {
    try {
      agent.post().uri("/internal/model/check")
          .body(Map.of("modelName", modelName, "timeoutSeconds", timeoutSeconds))
          .retrieve().toBodilessEntity();
      return Map.of("connected", true);
    } catch (RestClientResponseException e) {
      JsonNode error = e.getResponseBodyAs(JsonNode.class);
      String code = error == null ? "" : error.path("code").asText();
      String message = switch (code) {
        case "MODEL_NOT_CONFIGURED" -> "Agent 服务未配置模型地址、名称或密钥";
        case "MODEL_TIMEOUT" -> "模型服务连接检查超时";
        case "MODEL_CONNECTION_FAILED" -> "Agent 无法连接模型服务";
        case "MODEL_AUTH_FAILED" -> "模型服务拒绝认证，请核对服务端密钥";
        case "MODEL_NOT_FOUND" -> "模型服务未找到指定模型";
        default -> "模型服务连接检查失败，请检查 Agent 和模型服务配置";
      };
      throw new ApiException(502, "MODEL_CHECK_FAILED", message);
    } catch (ResourceAccessException e) {
      throw new ApiException(502, "AGENT_UNAVAILABLE", "无法连接 Agent 服务或连接检查超时");
    }
  }
}
