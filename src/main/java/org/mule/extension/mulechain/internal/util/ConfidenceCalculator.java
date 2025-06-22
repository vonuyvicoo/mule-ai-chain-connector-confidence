/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.internal.util;

import org.mule.extension.mulechain.api.config.ConfidenceStrategy;
import org.mule.extension.mulechain.api.metadata.ConfidenceScore;
import org.mule.extension.mulechain.api.model.LogProbsData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for calculating confidence scores from log probability data.
 */
public class ConfidenceCalculator {

  private ConfidenceCalculator() {
    // Utility class
  }

  /**
   * Calculate confidence score based on log probabilities data and strategy.
   *
   * @param logProbsData Log probabilities data from OpenAI API
   * @param strategy     Strategy to use for confidence calculation
   * @return Calculated confidence score
   */
  public static ConfidenceScore calculate(LogProbsData logProbsData, ConfidenceStrategy strategy) {
    if (logProbsData == null || logProbsData.isEmpty()) {
      return ConfidenceScore.unavailable();
    }

    try {
      switch (strategy) {
        case ENTROPY_BASED:
          return calculateEntropyBasedConfidence(logProbsData);
        case TOP_TOKEN_PROB:
          return calculateTopTokenConfidence(logProbsData);
        case AVERAGE_LOG_PROB:
          return calculateAverageLogProbConfidence(logProbsData);
        case WEIGHTED_ENTROPY:
          return calculateWeightedEntropyConfidence(logProbsData);
        case VARIANCE_BASED:
          return calculateVarianceBasedConfidence(logProbsData);
        default:
          return calculateEntropyBasedConfidence(logProbsData);
      }
    } catch (Exception e) {
      // If calculation fails, return unavailable confidence
      return ConfidenceScore.unavailable();
    }
  }

  /**
   * Calculate confidence based on entropy of token probability distribution.
   * Lower entropy indicates higher confidence.
   */
  private static ConfidenceScore calculateEntropyBasedConfidence(LogProbsData logProbsData) {
    List<LogProbsData.TokenLogProb> tokens = logProbsData.getTokens();
    double totalEntropy = 0.0;
    double maxPossibleEntropy = 0.0;

    Map<String, Double> metrics = new HashMap<>();

    for (LogProbsData.TokenLogProb tokenLogProb : tokens) {
      double entropy = calculateTokenEntropy(tokenLogProb);
      totalEntropy += entropy;

      // Maximum entropy for this token (assuming uniform distribution over
      // alternatives)
      int alternatives = Math.max(1, tokenLogProb.getTopLogprobs().size() + 1);
      maxPossibleEntropy += Math.log(alternatives) / Math.log(2); // log2
    }

    double averageEntropy = totalEntropy / tokens.size();
    double maxAvgEntropy = maxPossibleEntropy / tokens.size();

    // Normalize: high entropy = low confidence, so invert
    double confidence = maxAvgEntropy > 0 ? 1.0 - (averageEntropy / maxAvgEntropy) : 0.0;

    metrics.put("average_entropy", averageEntropy);
    metrics.put("max_possible_entropy", maxAvgEntropy);
    metrics.put("entropy_ratio", maxAvgEntropy > 0 ? averageEntropy / maxAvgEntropy : 0.0);

    return new ConfidenceScore(confidence, ConfidenceStrategy.ENTROPY_BASED.getValue(), metrics, tokens.size());
  }

  /**
   * Calculate confidence based on average probability of most likely tokens.
   */
  private static ConfidenceScore calculateTopTokenConfidence(LogProbsData logProbsData) {
    List<LogProbsData.TokenLogProb> tokens = logProbsData.getTokens();
    double totalProbability = 0.0;
    double minProbability = Double.MAX_VALUE;
    double maxProbability = Double.MIN_VALUE;

    Map<String, Double> metrics = new HashMap<>();

    for (LogProbsData.TokenLogProb tokenLogProb : tokens) {
      double probability = tokenLogProb.getProbability();
      totalProbability += probability;
      minProbability = Math.min(minProbability, probability);
      maxProbability = Math.max(maxProbability, probability);
    }

    double averageProbability = totalProbability / tokens.size();

    metrics.put("average_probability", averageProbability);
    metrics.put("min_probability", minProbability);
    metrics.put("max_probability", maxProbability);
    metrics.put("probability_range", maxProbability - minProbability);

    return new ConfidenceScore(averageProbability, ConfidenceStrategy.TOP_TOKEN_PROB.getValue(), metrics,
                               tokens.size());
  }

  /**
   * Calculate confidence based on average log probabilities.
   */
  private static ConfidenceScore calculateAverageLogProbConfidence(LogProbsData logProbsData) {
    List<LogProbsData.TokenLogProb> tokens = logProbsData.getTokens();
    double totalLogProb = 0.0;
    double minLogProb = Double.MAX_VALUE;
    double maxLogProb = Double.MIN_VALUE;

    Map<String, Double> metrics = new HashMap<>();

    for (LogProbsData.TokenLogProb tokenLogProb : tokens) {
      double logProb = tokenLogProb.getLogprob();
      totalLogProb += logProb;
      minLogProb = Math.min(minLogProb, logProb);
      maxLogProb = Math.max(maxLogProb, logProb);
    }

    double averageLogProb = totalLogProb / tokens.size();

    // Convert log probability to confidence (log probs are negative, closer to 0 is
    // better)
    // Normalize using a reasonable range assumption (log probs typically between
    // -10 and 0)
    double confidence = Math.exp(Math.max(-10.0, averageLogProb));

    metrics.put("average_log_prob", averageLogProb);
    metrics.put("min_log_prob", minLogProb);
    metrics.put("max_log_prob", maxLogProb);
    metrics.put("log_prob_range", maxLogProb - minLogProb);

    return new ConfidenceScore(confidence, ConfidenceStrategy.AVERAGE_LOG_PROB.getValue(), metrics, tokens.size());
  }

  /**
   * Calculate weighted entropy confidence considering token position.
   */
  private static ConfidenceScore calculateWeightedEntropyConfidence(LogProbsData logProbsData) {
    List<LogProbsData.TokenLogProb> tokens = logProbsData.getTokens();
    double weightedEntropy = 0.0;
    double totalWeight = 0.0;

    Map<String, Double> metrics = new HashMap<>();

    for (int i = 0; i < tokens.size(); i++) {
      LogProbsData.TokenLogProb tokenLogProb = tokens.get(i);

      // Weight decreases for later tokens (early tokens are more important)
      double weight = calculatePositionWeight(i, tokens.size());
      double entropy = calculateTokenEntropy(tokenLogProb);

      weightedEntropy += entropy * weight;
      totalWeight += weight;
    }

    double averageWeightedEntropy = totalWeight > 0 ? weightedEntropy / totalWeight : 0.0;

    // Estimate maximum weighted entropy and normalize
    double estimatedMaxEntropy = 3.0; // Reasonable assumption for max entropy per token
    double confidence = Math.max(0.0, 1.0 - (averageWeightedEntropy / estimatedMaxEntropy));

    metrics.put("weighted_entropy", averageWeightedEntropy);
    metrics.put("total_weight", totalWeight);
    metrics.put("estimated_max_entropy", estimatedMaxEntropy);

    return new ConfidenceScore(confidence, ConfidenceStrategy.WEIGHTED_ENTROPY.getValue(), metrics, tokens.size());
  }

  /**
   * Calculate confidence based on variance of token probabilities.
   */
  private static ConfidenceScore calculateVarianceBasedConfidence(LogProbsData logProbsData) {
    List<LogProbsData.TokenLogProb> tokens = logProbsData.getTokens();

    // First pass: calculate mean
    double meanProbability = 0.0;
    for (LogProbsData.TokenLogProb tokenLogProb : tokens) {
      meanProbability += tokenLogProb.getProbability();
    }
    meanProbability /= tokens.size();

    // Second pass: calculate variance
    double variance = 0.0;
    for (LogProbsData.TokenLogProb tokenLogProb : tokens) {
      double diff = tokenLogProb.getProbability() - meanProbability;
      variance += diff * diff;
    }
    variance /= tokens.size();

    double standardDeviation = Math.sqrt(variance);

    Map<String, Double> metrics = new HashMap<>();
    metrics.put("mean_probability", meanProbability);
    metrics.put("variance", variance);
    metrics.put("standard_deviation", standardDeviation);

    // Lower variance indicates higher confidence
    // Normalize variance to get confidence score
    double maxExpectedStdDev = 0.3; // Reasonable assumption
    double confidence = Math.max(0.0, 1.0 - (standardDeviation / maxExpectedStdDev));

    metrics.put("confidence_factor", confidence);

    return new ConfidenceScore(confidence, ConfidenceStrategy.VARIANCE_BASED.getValue(), metrics, tokens.size());
  }

  /**
   * Calculate entropy for a single token based on its alternatives.
   */
  private static double calculateTokenEntropy(LogProbsData.TokenLogProb tokenLogProb) {
    double entropy = 0.0;

    // Include the selected token
    double selectedProb = tokenLogProb.getProbability();
    if (selectedProb > 0) {
      entropy -= selectedProb * (Math.log(selectedProb) / Math.log(2));
    }

    // Include alternative tokens
    for (LogProbsData.TopLogProb topLogProb : tokenLogProb.getTopLogprobs()) {
      double prob = topLogProb.getProbability();
      if (prob > 0) {
        entropy -= prob * (Math.log(prob) / Math.log(2));
      }
    }

    return entropy;
  }

  /**
   * Calculate position weight for weighted entropy calculation.
   * Earlier positions have higher weight.
   */
  private static double calculatePositionWeight(int position, int totalTokens) {
    if (totalTokens <= 1) {
      return 1.0;
    }

    // Exponential decay: earlier tokens are more important
    double decay = 0.1; // Adjust this to control how quickly weight decreases
    return Math.exp(-decay * position);
  }
}
