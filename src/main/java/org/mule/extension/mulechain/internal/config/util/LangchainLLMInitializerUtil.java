/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.internal.config.util;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.huggingface.HuggingFaceChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.mule.extension.mulechain.internal.config.LangchainLLMConfiguration;
import org.mule.extension.mulechain.internal.llm.config.ConfigExtractor;

import static java.time.Duration.ofSeconds;

public final class LangchainLLMInitializerUtil {

  private LangchainLLMInitializerUtil() {}

  public static OpenAiChatModel createOpenAiChatModel(ConfigExtractor configExtractor,
                                                      LangchainLLMConfiguration configuration) {
    String openaiApiKey = configExtractor.extractValue("OPENAI_API_KEY");
    long durationInSec = configuration.getLlmTimeoutUnit().toSeconds(configuration.getLlmTimeout());

    boolean isReasoningModel = shouldUseMaxCompletionTokens(configuration.getModelName());

    // Add debug logging
    System.out.println("DEBUG: Creating OpenAI model with name: " + configuration.getModelName());
    System.out.println("DEBUG: Is reasoning model: " + isReasoningModel);

    if (isReasoningModel) {
      // For reasoning models, avoid using builder that has default temperature
      // Create minimal builder without temperature and topP
      System.out.println("DEBUG: Creating reasoning model without temperature/topP");
      return OpenAiChatModel.builder()
          .apiKey(openaiApiKey)
          .modelName(configuration.getModelName())
          .timeout(ofSeconds(durationInSec))
          .maxCompletionTokens(configuration.getMaxTokens())
          .logRequests(true)
          .logResponses(true)
          .build();
    } else {
      // For regular models, use normal builder with all parameters
      System.out.println("DEBUG: Creating regular model with temperature=" + configuration.getTemperature()
          + " and topP=" + configuration.getTopP());
      return OpenAiChatModel.builder()
          .apiKey(openaiApiKey)
          .modelName(configuration.getModelName())
          .timeout(ofSeconds(durationInSec))
          .temperature(configuration.getTemperature())
          .topP(configuration.getTopP())
          .maxTokens(configuration.getMaxTokens())
          .logRequests(true)
          .logResponses(true)
          .build();
    }
  }

  public static OpenAiChatModel createGroqOpenAiChatModel(ConfigExtractor configExtractor,
                                                          LangchainLLMConfiguration configuration) {
    String groqApiKey = configExtractor.extractValue("GROQ_API_KEY");
    long durationInSec = configuration.getLlmTimeoutUnit().toSeconds(configuration.getLlmTimeout());

    boolean isReasoningModel = shouldUseMaxCompletionTokens(configuration.getModelName());

    if (isReasoningModel) {
      // For reasoning models, avoid using builder that has default temperature
      System.out.println("DEBUG: Creating Groq reasoning model without temperature/topP");
      return OpenAiChatModel.builder()
          .baseUrl("https://api.groq.com/openai/v1")
          .apiKey(groqApiKey)
          .modelName(configuration.getModelName())
          .timeout(ofSeconds(durationInSec))
          .maxCompletionTokens(configuration.getMaxTokens())
          .logRequests(true)
          .logResponses(true)
          .build();
    } else {
      // For regular models, use normal builder with all parameters
      return OpenAiChatModel.builder()
          .baseUrl("https://api.groq.com/openai/v1")
          .apiKey(groqApiKey)
          .modelName(configuration.getModelName())
          .timeout(ofSeconds(durationInSec))
          .temperature(configuration.getTemperature())
          .topP(configuration.getTopP())
          .maxTokens(configuration.getMaxTokens())
          .logRequests(true)
          .logResponses(true)
          .build();
    }
  }

  public static MistralAiChatModel createMistralAiChatModel(ConfigExtractor configExtractor,
                                                            LangchainLLMConfiguration configuration) {
    String mistralAiApiKey = configExtractor.extractValue("MISTRAL_AI_API_KEY");
    long durationInSec = configuration.getLlmTimeoutUnit().toSeconds(configuration.getLlmTimeout());
    return MistralAiChatModel.builder()
        // .apiKey(configuration.getLlmApiKey())
        .apiKey(mistralAiApiKey)
        .modelName(configuration.getModelName())
        .maxTokens(configuration.getMaxTokens())
        .temperature(configuration.getTemperature())
        .topP(configuration.getTopP())
        .timeout(ofSeconds(durationInSec))
        .logRequests(true)
        .logResponses(true)
        .build();
  }

  public static OllamaChatModel createOllamaChatModel(ConfigExtractor configExtractor,
                                                      LangchainLLMConfiguration configuration) {
    String ollamaBaseUrl = configExtractor.extractValue("OLLAMA_BASE_URL");
    long durationInSec = configuration.getLlmTimeoutUnit().toSeconds(configuration.getLlmTimeout());
    return OllamaChatModel.builder()
        // .baseUrl(configuration.getLlmApiKey())
        .baseUrl(ollamaBaseUrl)
        .modelName(configuration.getModelName())
        .temperature(configuration.getTemperature())
        .topP(configuration.getTopP())
        .timeout(ofSeconds(durationInSec))
        .build();
  }

  public static HuggingFaceChatModel createHuggingFaceChatModel(ConfigExtractor configExtractor,
                                                                LangchainLLMConfiguration configuration) {
    String huggingFaceApiKey = configExtractor.extractValue("HUGGING_FACE_API_KEY");
    long durationInSec = configuration.getLlmTimeoutUnit().toSeconds(configuration.getLlmTimeout());
    return HuggingFaceChatModel.builder()
        .accessToken(huggingFaceApiKey)
        .modelId(configuration.getModelName())
        .timeout(ofSeconds(durationInSec))
        .temperature(configuration.getTemperature())
        .maxNewTokens(configuration.getMaxTokens())
        .waitForModel(true)
        .build();
  }

  public static GoogleAiGeminiChatModel createGoogleGeminiChatModel(ConfigExtractor configExtractor,
                                                                    LangchainLLMConfiguration configuration) {
    String geminiAiKey = configExtractor.extractValue("GEMINI_AI_KEY");
    long durationInSec = configuration.getLlmTimeoutUnit().toSeconds(configuration.getLlmTimeout());
    return GoogleAiGeminiChatModel.builder()
        .apiKey(geminiAiKey)
        .modelName(configuration.getModelName())
        .temperature(configuration.getTemperature())
        .topP(configuration.getTopP())
        .timeout(ofSeconds(durationInSec))
        .maxOutputTokens(configuration.getMaxTokens())
        .logRequestsAndResponses(false)
        .build();
  }

  public static AnthropicChatModel createAnthropicChatModel(ConfigExtractor configExtractor,
                                                            LangchainLLMConfiguration configuration) {
    String anthropicApiKey = configExtractor.extractValue("ANTHROPIC_API_KEY");
    long durationInSec = configuration.getLlmTimeoutUnit().toSeconds(configuration.getLlmTimeout());
    return AnthropicChatModel.builder()
        // .apiKey(configuration.getLlmApiKey())
        .apiKey(anthropicApiKey)
        .modelName(configuration.getModelName())
        .maxTokens(configuration.getMaxTokens())
        .temperature(configuration.getTemperature())
        .topP(configuration.getTopP())
        .timeout(ofSeconds(durationInSec))
        .logRequests(true)
        .logResponses(true)
        .build();
  }

  public static AzureOpenAiChatModel createAzureOpenAiChatModel(ConfigExtractor configExtractor,
                                                                LangchainLLMConfiguration configuration) {
    String azureOpenaiKey = configExtractor.extractValue("AZURE_OPENAI_KEY");
    String azureOpenaiEndpoint = configExtractor.extractValue("AZURE_OPENAI_ENDPOINT");
    String azureOpenaiDeploymentName = configExtractor.extractValue("AZURE_OPENAI_DEPLOYMENT_NAME");
    long durationInSec = configuration.getLlmTimeoutUnit().toSeconds(configuration.getLlmTimeout());

    boolean isReasoningModel = shouldUseMaxCompletionTokens(configuration.getModelName());

    if (isReasoningModel) {
      // For reasoning models, avoid using builder that has default temperature
      System.out.println("DEBUG: Creating Azure reasoning model without temperature/topP");
      return AzureOpenAiChatModel.builder()
          .apiKey(azureOpenaiKey)
          .endpoint(azureOpenaiEndpoint)
          .deploymentName(azureOpenaiDeploymentName)
          .timeout(ofSeconds(durationInSec))
          .maxTokens(configuration.getMaxTokens()) // Azure doesn't support maxCompletionTokens yet
          .logRequestsAndResponses(true)
          .build();
    } else {
      // For regular models, use normal builder with all parameters
      return AzureOpenAiChatModel.builder()
          .apiKey(azureOpenaiKey)
          .endpoint(azureOpenaiEndpoint)
          .deploymentName(azureOpenaiDeploymentName)
          .timeout(ofSeconds(durationInSec))
          .temperature(configuration.getTemperature())
          .topP(configuration.getTopP())
          .maxTokens(configuration.getMaxTokens())
          .logRequestsAndResponses(true)
          .build();
    }
  }

  /**
   * Determines if the model requires max_completion_tokens instead of max_tokens.
   * OpenAI models starting with "o" (like o1, o1-preview, o1-mini) or reasoning
   * models
   * require max_completion_tokens instead of max_tokens.
   */
  private static boolean shouldUseMaxCompletionTokens(String model) {
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
}
