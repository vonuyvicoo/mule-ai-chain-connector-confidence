/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.internal.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.mule.extension.mulechain.api.config.ConfidenceStrategy;
import org.mule.extension.mulechain.internal.operation.LangchainEmbeddingStoresOperations;
import org.mule.extension.mulechain.internal.operation.LangchainImageModelsOperations;
import org.mule.extension.mulechain.internal.llm.type.LangchainLLMType;
import org.mule.extension.mulechain.internal.llm.ConfigTypeProvider;
import org.mule.extension.mulechain.internal.operation.LangchainLLMOperations;
import org.mule.extension.mulechain.internal.llm.LangchainLLMModelNameProvider;
import org.mule.extension.mulechain.internal.llm.LangchainLLMTypeProvider;
import org.mule.extension.mulechain.internal.llm.config.ConfigExtractor;
import org.mule.extension.mulechain.internal.llm.config.ConfigType;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.annotation.values.OfValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * This class represents an extension configuration, values set in this class
 * are commonly used across multiple
 * operations since they represent something core from the extension.
 */
@Configuration(name = "config")
@Operations({LangchainLLMOperations.class, LangchainEmbeddingStoresOperations.class,
    LangchainImageModelsOperations.class})
public class LangchainLLMConfiguration implements Initialisable {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangchainLLMConfiguration.class);

  @Parameter
  @Placement(order = 1, tab = Placement.DEFAULT_TAB)
  @DisplayName("LLM type")
  @OfValues(LangchainLLMTypeProvider.class)
  private String llmType;

  @Parameter
  @Placement(order = 2, tab = Placement.DEFAULT_TAB)
  @OfValues(ConfigTypeProvider.class)
  private String configType;

  @Parameter
  @Placement(order = 3, tab = Placement.DEFAULT_TAB)
  private String filePath;

  @Parameter
  @Expression(ExpressionSupport.SUPPORTED)
  @OfValues(LangchainLLMModelNameProvider.class)
  @Placement(order = 4, tab = Placement.DEFAULT_TAB)
  private String modelName;

  @Parameter
  @Placement(order = 5, tab = Placement.DEFAULT_TAB)
  @Optional(defaultValue = "0.7")
  private double temperature = 0.7;

  @Parameter
  @Placement(order = 6, tab = Placement.DEFAULT_TAB)
  @Optional(defaultValue = "0.95")
  private double topP = 0.95;

  @Parameter
  @Placement(order = 7, tab = Placement.DEFAULT_TAB)
  @Optional(defaultValue = "120")
  @DisplayName("LLM timeout")
  private int llmTimeout = 120;

  @Parameter
  @Optional(defaultValue = "SECONDS")
  @Placement(order = 8, tab = Placement.DEFAULT_TAB)
  @DisplayName("LLM timeout unit")
  @Summary("Time unit to be used in the LLM Timeout")
  private TimeUnit llmTimeoutUnit = TimeUnit.SECONDS;

  @Parameter
  @Placement(order = 9, tab = Placement.DEFAULT_TAB)
  @Expression(ExpressionSupport.SUPPORTED)
  @Optional(defaultValue = "500")
  private int maxTokens = 500;

  @Parameter
  @Optional(defaultValue = "false")
  @Placement(order = 10, tab = "Advanced")
  @DisplayName("Enable Confidence Score")
  @Summary("Enable confidence score calculation for LLM responses (OpenAI only)")
  private boolean enableConfidenceScore = false;

  @Parameter
  @Optional(defaultValue = "ENTROPY_BASED")
  @Placement(order = 11, tab = "Advanced")
  @DisplayName("Confidence Strategy")
  @Summary("Strategy for calculating confidence scores")
  private ConfidenceStrategy confidenceStrategy = ConfidenceStrategy.ENTROPY_BASED;

  private ConfigExtractor configExtractor;

  private ChatLanguageModel model;

  public String getLlmType() {
    return llmType;
  }

  public String getConfigType() {
    return configType;
  }

  public String getFilePath() {
    return filePath;
  }

  public String getModelName() {
    return modelName;
  }

  public double getTemperature() {
    return temperature;
  }

  public double getTopP() {
    return topP;
  }

  public int getLlmTimeout() {
    return llmTimeout;
  }

  public TimeUnit getLlmTimeoutUnit() {
    return llmTimeoutUnit;
  }

  public int getMaxTokens() {
    return maxTokens;
  }

  public boolean getEnableConfidenceScore() {
    return enableConfidenceScore;
  }

  public ConfidenceStrategy getConfidenceStrategy() {
    return confidenceStrategy;
  }

  public ConfigExtractor getConfigExtractor() {
    return configExtractor;
  }

  public ChatLanguageModel getModel() {
    return model;
  }

  private ChatLanguageModel createModel(ConfigExtractor configExtractor) {
    LangchainLLMType type = LangchainLLMType.fromValue(llmType);
    LOGGER.debug("Creating Model of LLMType: {}", llmType);
    return type.getConfigBiFunction().apply(configExtractor, this);
  }

  @Override
  public void initialise() throws InitialisationException {
    if (configType != null) {
      ConfigType config = ConfigType.fromValue(configType);
      configExtractor = config.getConfigExtractorFunction().apply(this);
      model = createModel(configExtractor);
    } else {
      LOGGER.warn("Initialisation is called before the values are populated");
    }
  }
}
