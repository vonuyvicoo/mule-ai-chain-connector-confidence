/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.api.metadata;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ConfidenceScoreTest {

  @Test
  public void testConfidenceScoreCreation() {
    Map<String, Double> metrics = new HashMap<>();
    metrics.put("entropy", 0.5);
    metrics.put("variance", 0.2);

    ConfidenceScore score = new ConfidenceScore(0.8, "ENTROPY_BASED", metrics, 10);

    assertEquals("Score should match", 0.8, score.getScore(), 0.001);
    assertEquals("Strategy should match", "ENTROPY_BASED", score.getStrategy());
    assertEquals("Token count should match", 10, score.getTotalTokens());
    assertTrue("Confidence should be available", score.isAvailable());
    assertEquals("Confidence level should be HIGH", "HIGH", score.getConfidenceLevel());
  }

  @Test
  public void testConfidenceScoreClampingUpperBound() {
    ConfidenceScore score = new ConfidenceScore(1.5, "TEST", new HashMap<>(), 5);
    assertEquals("Score should be clamped to 1.0", 1.0, score.getScore(), 0.001);
  }

  @Test
  public void testConfidenceScoreClampingLowerBound() {
    ConfidenceScore score = new ConfidenceScore(-0.5, "TEST", new HashMap<>(), 5);
    assertEquals("Score should be clamped to 0.0", 0.0, score.getScore(), 0.001);
  }

  @Test
  public void testUnavailableConfidenceScore() {
    ConfidenceScore score = ConfidenceScore.unavailable();

    assertFalse("Confidence should not be available", score.isAvailable());
    assertEquals("Strategy should be UNAVAILABLE", "UNAVAILABLE", score.getStrategy());
    assertEquals("Confidence level should be UNAVAILABLE", "UNAVAILABLE", score.getConfidenceLevel());
    assertEquals("Token count should be 0", 0, score.getTotalTokens());
    assertTrue("Metrics should be empty", score.getMetrics().isEmpty());
  }

  @Test
  public void testConfidenceLevels() {
    assertEquals("Score 0.95 should be VERY_HIGH", "VERY_HIGH",
                 new ConfidenceScore(0.95, "TEST", new HashMap<>(), 1).getConfidenceLevel());
    assertEquals("Score 0.8 should be HIGH", "HIGH",
                 new ConfidenceScore(0.8, "TEST", new HashMap<>(), 1).getConfidenceLevel());
    assertEquals("Score 0.6 should be MEDIUM", "MEDIUM",
                 new ConfidenceScore(0.6, "TEST", new HashMap<>(), 1).getConfidenceLevel());
    assertEquals("Score 0.3 should be LOW", "LOW",
                 new ConfidenceScore(0.3, "TEST", new HashMap<>(), 1).getConfidenceLevel());
    assertEquals("Score 0.1 should be VERY_LOW", "VERY_LOW",
                 new ConfidenceScore(0.1, "TEST", new HashMap<>(), 1).getConfidenceLevel());
  }

  @Test
  public void testMetricsImmutability() {
    Map<String, Double> metrics = new HashMap<>();
    metrics.put("test", 1.0);

    ConfidenceScore score = new ConfidenceScore(0.5, "TEST", metrics, 1);

    // Try to modify the original map
    metrics.put("new_metric", 2.0);

    // The confidence score's metrics should not be affected
    assertFalse("Metrics should not contain the new metric",
                score.getMetrics().containsKey("new_metric"));
    assertEquals("Original metric should still be present",
                 1.0, score.getMetrics().get("test"), 0.001);
  }

  @Test
  public void testToString() {
    Map<String, Double> metrics = new HashMap<>();
    metrics.put("entropy", 0.5);

    ConfidenceScore score = new ConfidenceScore(0.75, "ENTROPY_BASED", metrics, 5);
    String toString = score.toString();

    assertTrue("toString should contain score", toString.contains("0.750"));
    assertTrue("toString should contain level", toString.contains("HIGH"));
    assertTrue("toString should contain strategy", toString.contains("ENTROPY_BASED"));
    assertTrue("toString should contain token count", toString.contains("5"));
    assertTrue("toString should contain availability", toString.contains("true"));
  }
}
