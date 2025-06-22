/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.api.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents log probability data from OpenAI API responses.
 */
public class LogProbsData implements Serializable {

  private final List<TokenLogProb> tokens;
  private final Map<String, Object> metadata;

  public LogProbsData(List<TokenLogProb> tokens, Map<String, Object> metadata) {
    this.tokens = tokens != null ? Collections.unmodifiableList(tokens) : Collections.emptyList();
    this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
  }

  public List<TokenLogProb> getTokens() {
    return tokens;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public boolean isEmpty() {
    return tokens.isEmpty();
  }

  public int getTokenCount() {
    return tokens.size();
  }

  /**
   * Represents a single token with its log probability information.
   */
  public static class TokenLogProb implements Serializable {

    private final String token;
    private final double logprob;
    private final List<TopLogProb> topLogprobs;
    private final byte[] bytes;

    public TokenLogProb(String token, double logprob, List<TopLogProb> topLogprobs, byte[] bytes) {
      this.token = token;
      this.logprob = logprob;
      this.topLogprobs = topLogprobs != null ? Collections.unmodifiableList(topLogprobs)
          : Collections.emptyList();
      this.bytes = bytes != null ? bytes.clone() : new byte[0];
    }

    public String getToken() {
      return token;
    }

    public double getLogprob() {
      return logprob;
    }

    public List<TopLogProb> getTopLogprobs() {
      return topLogprobs;
    }

    public byte[] getBytes() {
      return bytes.clone();
    }

    /**
     * @return Probability value (exp of log probability)
     */
    public double getProbability() {
      return Math.exp(logprob);
    }
  }

  /**
   * Represents alternative tokens with their log probabilities.
   */
  public static class TopLogProb implements Serializable {

    private final String token;
    private final double logprob;
    private final byte[] bytes;

    public TopLogProb(String token, double logprob, byte[] bytes) {
      this.token = token;
      this.logprob = logprob;
      this.bytes = bytes != null ? bytes.clone() : new byte[0];
    }

    public String getToken() {
      return token;
    }

    public double getLogprob() {
      return logprob;
    }

    public byte[] getBytes() {
      return bytes.clone();
    }

    /**
     * @return Probability value (exp of log probability)
     */
    public double getProbability() {
      return Math.exp(logprob);
    }
  }
}
