/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.internal.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.mule.extension.mulechain.api.model.LogProbsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Direct OpenAI API client for making requests with logprobs support.
 * This is a temporary solution until langchain4j supports logprobs.
 */
public class OpenAiLogProbsClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiLogProbsClient.class);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final String apiKey;
  private final String baseUrl;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OpenAiLogProbsClient(String apiKey, String baseUrl, Duration timeout) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";

    long timeoutSeconds = timeout != null ? timeout.getSeconds() : 60L;
    this.httpClient = new OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Make a chat completion request with logprobs enabled.
   */
  public LogProbsData getChatCompletionLogProbs(String model, List<ChatMessage> messages,
                                                Double temperature, Double topP, Integer maxTokens)
      throws IOException {

    ChatCompletionRequest request = new ChatCompletionRequest();
    request.model = model;
    request.messages = messages;

    boolean isReasoningModel = shouldUseMaxCompletionTokens(model);

    // Only set temperature and topP for non-reasoning models
    if (!isReasoningModel) {
      request.temperature = temperature;
      request.topP = topP;
    } else {
      // Explicitly set to null for reasoning models to ensure they're excluded from
      // JSON
      request.temperature = null;
      request.topP = null;
    }

    // Use appropriate token limit field based on model
    if (isReasoningModel) {
      request.maxCompletionTokens = maxTokens;
      request.maxTokens = null; // Don't set max_tokens for reasoning models
      LOGGER.debug("Using max_completion_tokens={} for reasoning model: {} (temperature/topP excluded)",
                   maxTokens, model);
    } else {
      request.maxTokens = maxTokens;
      request.maxCompletionTokens = null; // Don't set max_completion_tokens for regular models
      LOGGER.debug("Using max_tokens={} for model: {} with temperature={}, topP={}", maxTokens, model,
                   temperature, topP);
    }

    request.logprobs = true;
    request.topLogprobs = 5; // Get top 5 alternative tokens

    String requestJson = objectMapper.writeValueAsString(request);
    LOGGER.debug("OpenAI request JSON: {}", requestJson);

    RequestBody body = RequestBody.create(requestJson, JSON);
    Request httpRequest = new Request.Builder()
        .url(baseUrl + "/chat/completions")
        .header("Authorization", "Bearer " + apiKey)
        .header("User-Agent", "MuleSoft-AI-Chain-Connector/1.2.0")
        .post(body)
        .build();

    try (Response response = httpClient.newCall(httpRequest).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException(
                              "OpenAI API request failed with status " + response.code() + ": " + response.message());
      }

      String responseBody = response.body().string();
      ChatCompletionResponse chatResponse = objectMapper.readValue(responseBody, ChatCompletionResponse.class);
      return extractLogProbsData(chatResponse);
    }
  }

  /**
   * Extract LogProbsData from OpenAI response.
   */
  private LogProbsData extractLogProbsData(ChatCompletionResponse response) {
    if (response.choices == null || response.choices.isEmpty()) {
      return new LogProbsData(Collections.emptyList(), Collections.emptyMap());
    }

    Choice choice = response.choices.get(0);
    if (choice.logprobs == null || choice.logprobs.content == null) {
      return new LogProbsData(Collections.emptyList(), Collections.emptyMap());
    }

    List<LogProbsData.TokenLogProb> tokens = new ArrayList<>();
    for (LogProbsContent content : choice.logprobs.content) {
      List<LogProbsData.TopLogProb> topLogProbs = new ArrayList<>();

      if (content.topLogprobs != null) {
        for (TopLogProb topLogProb : content.topLogprobs) {
          topLogProbs.add(new LogProbsData.TopLogProb(
                                                      topLogProb.token,
                                                      topLogProb.logprob,
                                                      topLogProb.bytes));
        }
      }

      tokens.add(new LogProbsData.TokenLogProb(
                                               content.token,
                                               content.logprob,
                                               topLogProbs,
                                               content.bytes));
    }

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("model", response.model);
    metadata.put("created", response.created);
    metadata.put("finish_reason", choice.finishReason);
    if (response.usage != null) {
      metadata.put("prompt_tokens", response.usage.promptTokens);
      metadata.put("completion_tokens", response.usage.completionTokens);
      metadata.put("total_tokens", response.usage.totalTokens);
    }

    return new LogProbsData(tokens, metadata);
  }

  /**
   * Determines if the model requires max_completion_tokens instead of max_tokens.
   * OpenAI models starting with "o" (like o1, o1-preview, o1-mini) or reasoning
   * models
   * require max_completion_tokens instead of max_tokens.
   */
  private boolean shouldUseMaxCompletionTokens(String model) {
    if (model == null) {
      return false;
    }

    String lowerModel = model.toLowerCase();

    // Check if model starts with "o" followed by a digit or dash (o1, o1-preview,
    // o1-mini, etc.)
    if (lowerModel.matches("^o[0-9].*") || lowerModel.matches("^o-.*")) {
      return true;
    }

    // Check for reasoning models
    if (lowerModel.contains("reasoning")) {
      return true;
    }

    return false;
  }

  // Request/Response DTOs

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ChatCompletionRequest {

    public String model;
    public List<ChatMessage> messages;
    public Double temperature;
    @JsonProperty("top_p")
    public Double topP;
    @JsonProperty("max_tokens")
    public Integer maxTokens;
    @JsonProperty("max_completion_tokens")
    public Integer maxCompletionTokens;
    public Boolean logprobs;
    @JsonProperty("top_logprobs")
    public Integer topLogprobs;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ChatMessage {

    public String role;
    public String content;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
      this.role = role;
      this.content = content;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ChatCompletionResponse {

    public String id;
    public String object;
    public Long created;
    public String model;
    public List<Choice> choices;
    public Usage usage;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Choice {

    public Integer index;
    public ChatMessage message;
    public Logprobs logprobs;
    @JsonProperty("finish_reason")
    public String finishReason;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Logprobs {

    public List<LogProbsContent> content;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class LogProbsContent {

    public String token;
    public Double logprob;
    public byte[] bytes;
    @JsonProperty("top_logprobs")
    public List<TopLogProb> topLogprobs;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TopLogProb {

    public String token;
    public Double logprob;
    public byte[] bytes;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Usage {

    @JsonProperty("prompt_tokens")
    public Integer promptTokens;
    @JsonProperty("completion_tokens")
    public Integer completionTokens;
    @JsonProperty("total_tokens")
    public Integer totalTokens;
  }
}
