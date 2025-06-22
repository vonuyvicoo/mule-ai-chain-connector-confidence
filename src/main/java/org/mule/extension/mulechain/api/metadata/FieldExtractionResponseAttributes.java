/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.api.metadata;

import java.util.Map;

/**
 * Response attributes for field extraction operations.
 */
public class FieldExtractionResponseAttributes {

  private final int totalPages;
  private final Map<String, String> attributes;

  public FieldExtractionResponseAttributes(int totalPages, Map<String, String> attributes) {
    this.totalPages = totalPages;
    this.attributes = attributes;
  }

  public int getTotalPages() {
    return totalPages;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }

  /**
   * Represents a single extracted field with its value and confidence score.
   */
  public static class ExtractedField {

    private final String fieldName;
    private final String value;
    private final ConfidenceScore confidenceScore;
    private final int pageNumber;

    public ExtractedField(String fieldName, String value, ConfidenceScore confidenceScore, int pageNumber) {
      this.fieldName = fieldName;
      this.value = value;
      this.confidenceScore = confidenceScore;
      this.pageNumber = pageNumber;
    }

    public String getFieldName() {
      return fieldName;
    }

    public String getValue() {
      return value;
    }

    public ConfidenceScore getConfidenceScore() {
      return confidenceScore;
    }

    public int getPageNumber() {
      return pageNumber;
    }
  }
}
