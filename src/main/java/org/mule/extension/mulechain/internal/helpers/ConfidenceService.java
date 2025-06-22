/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.internal.helpers;

import org.mule.extension.mulechain.api.metadata.ConfidenceScore;
import org.mule.extension.mulechain.api.model.LogProbsData;
import org.mule.extension.mulechain.internal.client.OpenAiLogProbsClient;
import org.mule.extension.mulechain.internal.config.LangchainLLMConfiguration;
import org.mule.extension.mulechain.internal.llm.type.LangchainLLMType;
import org.mule.extension.mulechain.internal.util.ConfidenceCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper service for confidence score calculation functionality.
 */
public final class ConfidenceService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfidenceService.class);

  private ConfidenceService() {
    // Utility class
  }

  /**
   * Calculate confidence score for the given prompt response.
   * 
   * @param prompt        The original prompt
   * @param response      The LLM response
   * @param configuration The LLM configuration
   * @return Calculated confidence score or unavailable if not supported
   */
  public static ConfidenceScore calculateConfidence(String prompt, String response,
                                                    LangchainLLMConfiguration configuration) {
    if (!configuration.getEnableConfidenceScore()) {
      return ConfidenceScore.unavailable();
    }

    if (!isOpenAiModel(configuration)) {
      LOGGER.debug("Confidence calculation is only supported for OpenAI models. Current model: {}",
                   configuration.getLlmType());
      return ConfidenceScore.unavailable();
    }

    try {
      return calculateOpenAiConfidence(prompt, response, configuration);
    } catch (Exception e) {
      LOGGER.warn("Failed to calculate confidence score: {}", e.getMessage());
      LOGGER.debug("Confidence calculation error details", e);
      return ConfidenceScore.unavailable();
    }
  }

  /**
   * Calculate confidence for OpenAI models using direct API call with logprobs.
   */
  private static ConfidenceScore calculateOpenAiConfidence(String prompt, String response,
                                                           LangchainLLMConfiguration configuration)
      throws Exception {
    String apiKey = configuration.getConfigExtractor().extractValue("OPENAI_API_KEY");
    String baseUrl = getOpenAiBaseUrl(configuration);

    // Convert TimeUnit to Duration
    Duration timeout;
    switch (configuration.getLlmTimeoutUnit()) {
      case SECONDS:
        timeout = Duration.ofSeconds(configuration.getLlmTimeout());
        break;
      case MINUTES:
        timeout = Duration.ofMinutes(configuration.getLlmTimeout());
        break;
      case HOURS:
        timeout = Duration.ofHours(configuration.getLlmTimeout());
        break;
      default:
        timeout = Duration.ofSeconds(configuration.getLlmTimeout());
        break;
    }

    OpenAiLogProbsClient client = new OpenAiLogProbsClient(apiKey, baseUrl, timeout);

    // Prepare messages in OpenAI format
    List<OpenAiLogProbsClient.ChatMessage> messages = new ArrayList<>();
    messages.add(new OpenAiLogProbsClient.ChatMessage("user", prompt));

    // Make the API call to get logprobs
    LogProbsData logProbsData = client.getChatCompletionLogProbs(
                                                                 configuration.getModelName(),
                                                                 messages,
                                                                 configuration.getTemperature(),
                                                                 configuration.getTopP(),
                                                                 configuration.getMaxTokens());

    // Calculate confidence using the specified strategy
    return ConfidenceCalculator.calculate(logProbsData, configuration.getConfidenceStrategy());
  }

  /**
   * Check if the current model is an OpenAI model that supports logprobs.
   */
  private static boolean isOpenAiModel(LangchainLLMConfiguration configuration) {
    String llmType = configuration.getLlmType();
    return LangchainLLMType.OPENAI.getValue().equals(llmType) ||
        LangchainLLMType.GROQAI_OPENAI.getValue().equals(llmType); // Groq uses OpenAI API format
  }

  /**
   * Get the appropriate base URL for OpenAI API calls.
   */
  private static String getOpenAiBaseUrl(LangchainLLMConfiguration configuration) {
    String llmType = configuration.getLlmType();
    if (LangchainLLMType.GROQAI_OPENAI.getValue().equals(llmType)) {
      return "https://api.groq.com/openai/v1";
    }
    return "https://api.openai.com/v1"; // Default OpenAI URL
  }
}
