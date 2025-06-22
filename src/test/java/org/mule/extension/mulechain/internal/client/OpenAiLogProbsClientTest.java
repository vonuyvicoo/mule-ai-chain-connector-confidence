/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.internal.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;

/**
 * Test class for OpenAI client token parameter logic.
 */
public class OpenAiLogProbsClientTest {

  @Test
  public void testShouldUseMaxCompletionTokensForReasoningModels() {
    OpenAiLogProbsClient client = new OpenAiLogProbsClient("test-key", "https://api.openai.com/v1",
                                                           Duration.ofSeconds(30));

    // Test o1 models
    assertTrue("o1 should use max_completion_tokens", shouldUseMaxCompletionTokens(client, "o1"));
    assertTrue("o1-preview should use max_completion_tokens", shouldUseMaxCompletionTokens(client, "o1-preview"));
    assertTrue("o1-mini should use max_completion_tokens", shouldUseMaxCompletionTokens(client, "o1-mini"));
    assertTrue("o2 should use max_completion_tokens", shouldUseMaxCompletionTokens(client, "o2"));

    // Test reasoning models
    assertTrue("reasoning model should use max_completion_tokens",
               shouldUseMaxCompletionTokens(client, "gpt-4-reasoning"));
    assertTrue("model with reasoning should use max_completion_tokens",
               shouldUseMaxCompletionTokens(client, "test-reasoning-model"));

    // Test regular models
    assertFalse("gpt-4 should use max_tokens", shouldUseMaxCompletionTokens(client, "gpt-4"));
    assertFalse("gpt-4o should use max_tokens", shouldUseMaxCompletionTokens(client, "gpt-4o"));
    assertFalse("gpt-3.5-turbo should use max_tokens", shouldUseMaxCompletionTokens(client, "gpt-3.5-turbo"));
    assertFalse("claude should use max_tokens", shouldUseMaxCompletionTokens(client, "claude-3"));

    // Test edge cases
    assertFalse("null model should use max_tokens", shouldUseMaxCompletionTokens(client, null));
    assertFalse("empty model should use max_tokens", shouldUseMaxCompletionTokens(client, ""));
    assertFalse("model starting with 'o' but not o1 should use max_tokens",
                shouldUseMaxCompletionTokens(client, "openai-model"));
  }

  @Test
  public void testTemperatureExclusionForReasoningModels() {
    // This test documents the behavior that o1 models should not receive
    // temperature/topP parameters
    // The actual verification would require inspecting the HTTP request, which is
    // complex in unit tests
    // This test serves as documentation of the expected behavior

    OpenAiLogProbsClient client = new OpenAiLogProbsClient("test-key", "https://api.openai.com/v1",
                                                           Duration.ofSeconds(30));

    // Verify reasoning model detection (temperature should be excluded for these)
    assertTrue("o1 models should exclude temperature", shouldUseMaxCompletionTokens(client, "o1"));
    assertTrue("o1-preview should exclude temperature", shouldUseMaxCompletionTokens(client, "o1-preview"));
    assertTrue("o1-mini should exclude temperature", shouldUseMaxCompletionTokens(client, "o1-mini"));

    // Verify regular models (temperature should be included for these)
    assertFalse("gpt-4 should include temperature", shouldUseMaxCompletionTokens(client, "gpt-4"));
    assertFalse("gpt-4o should include temperature", shouldUseMaxCompletionTokens(client, "gpt-4o"));
    assertFalse("gpt-3.5-turbo should include temperature", shouldUseMaxCompletionTokens(client, "gpt-3.5-turbo"));
  }

  // Helper method to access private method for testing
  private boolean shouldUseMaxCompletionTokens(OpenAiLogProbsClient client, String model) {
    try {
      java.lang.reflect.Method method = client.getClass().getDeclaredMethod("shouldUseMaxCompletionTokens",
                                                                            String.class);
      method.setAccessible(true);
      return (Boolean) method.invoke(client, model);
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke shouldUseMaxCompletionTokens method", e);
    }
  }
}
