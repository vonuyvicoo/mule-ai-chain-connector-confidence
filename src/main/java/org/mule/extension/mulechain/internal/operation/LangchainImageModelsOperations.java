/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.internal.operation;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mule.extension.mulechain.api.metadata.LLMResponseAttributes;
import org.mule.extension.mulechain.api.metadata.ScannedDocResponseAttributes;
import org.mule.extension.mulechain.api.metadata.TokenUsage;
import org.mule.extension.mulechain.api.metadata.ConfidenceScore;
import org.mule.extension.mulechain.api.metadata.FieldExtractionResponseAttributes;
import org.mule.extension.mulechain.internal.config.LangchainLLMConfiguration;
import org.mule.extension.mulechain.internal.constants.MuleChainConstants;
import org.mule.extension.mulechain.internal.error.MuleChainErrorType;
import org.mule.extension.mulechain.internal.error.provider.ImageErrorTypeProvider;
import org.mule.extension.mulechain.internal.helpers.ConfidenceService;
import org.mule.extension.mulechain.internal.llm.config.ConfigExtractor;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.metadata.fixed.OutputJsonType;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import static org.apache.commons.io.IOUtils.toInputStream;
import static org.mule.extension.mulechain.internal.helpers.ResponseHelper.createLLMResponse;
import static org.mule.runtime.extension.api.annotation.param.MediaType.APPLICATION_JSON;

import dev.langchain4j.model.openai.OpenAiImageModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * This class is a container for Image related operations.Every public method in
 * this class will be taken as an extension operation.
 */
public class LangchainImageModelsOperations {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangchainImageModelsOperations.class);

  /**
   * Reads an image from a URL and provides the responses for the user prompts.
   *
   * @param configuration Refers to the configuration object
   * @param data          Refers to the user prompt
   * @param contextURL    Refers to the image URL to be analyzed
   * @return Refers to the response returned by the LLM
   */
  @MediaType(value = APPLICATION_JSON, strict = false)
  @Alias("IMAGE-read")
  @Throws(ImageErrorTypeProvider.class)
  @OutputJsonType(schema = "api/response/Response.json")
  public org.mule.runtime.extension.api.runtime.operation.Result<InputStream, LLMResponseAttributes> readFromImage(
                                                                                                                   @Config LangchainLLMConfiguration configuration,
                                                                                                                   @Content String data,
                                                                                                                   String contextURL) {
    try {
      LOGGER.debug("Image Read Operation called with the prompt: {} & the url: {}", data, contextURL);
      ChatLanguageModel model = configuration.getModel();

      UserMessage userMessage;
      if (isURL(contextURL)) {
        userMessage = UserMessage.from(
                                       TextContent.from(data),
                                       ImageContent.from(contextURL));
      } else {
        String imagePath = contextURL;
        String imageBase64 = convertToBase64String(imagePath);

        userMessage = UserMessage.from(
                                       TextContent.from(data),
                                       ImageContent.from(imageBase64, "image/png"));
      }

      Response<AiMessage> response = model.generate(userMessage);

      JSONObject jsonObject = new JSONObject();
      jsonObject.put(MuleChainConstants.RESPONSE, response.content().text());

      LOGGER.debug("Image Read Operation completed with the response: {}", response.content().text());

      // Calculate confidence score if enabled
      ConfidenceScore confidenceScore = null;
      if (configuration.getEnableConfidenceScore()) {
        try {
          confidenceScore = ConfidenceService.calculateConfidence(
                                                                  data, // Input prompt
                                                                  response.content().text(), // AI response
                                                                  configuration);
          LOGGER.debug("Confidence score calculated for image read: {}", confidenceScore.getScore());
        } catch (Exception e) {
          LOGGER.warn("Failed to calculate confidence score for image read: {}", e.getMessage());
        }
      }

      return createLLMResponse(jsonObject.toString(), response, new HashMap<>(), confidenceScore);
    } catch (Exception e) {
      throw new ModuleException(
                                String.format("Unable to analyze the provided image %s with the text: %s", contextURL,
                                              data),
                                MuleChainErrorType.IMAGE_ANALYSIS_FAILURE,
                                e);
    }
  }

  /**
   * Generates an image based on the prompt in data
   * 
   * @param configuration Refers to the configuration object
   * @param data          Refers to the user prompt
   * @return Returns the image URL link in the response
   */
  @MediaType(value = APPLICATION_JSON, strict = false)
  @Alias("IMAGE-generate")
  @Throws(ImageErrorTypeProvider.class)
  @OutputJsonType(schema = "api/response/Response.json")
  public org.mule.runtime.extension.api.runtime.operation.Result<InputStream, Void> drawImage(
                                                                                              @Config LangchainLLMConfiguration configuration,
                                                                                              @Content String data) {
    try {
      LOGGER.debug("Image Generate Operation called with the prompt: {}", data);
      ConfigExtractor configExtractor = configuration.getConfigExtractor();
      ImageModel model = OpenAiImageModel.builder()
          .modelName(configuration.getModelName())
          .apiKey(configExtractor.extractValue("OPENAI_API_KEY"))
          .build();

      Response<Image> response = model.generate(data);
      LOGGER.info("Generated Image: {}", response.content().url());

      JSONObject jsonObject = new JSONObject();
      jsonObject.put(MuleChainConstants.RESPONSE, response.content().url());

      LOGGER.debug("Image Generate Operation completed successfully with the image: {}",
                   response.content().url());
      return Result.<InputStream, Void>builder()
          .attributesMediaType(org.mule.runtime.api.metadata.MediaType.APPLICATION_JAVA)
          .output(toInputStream(jsonObject.toString(), StandardCharsets.UTF_8))
          .mediaType(org.mule.runtime.api.metadata.MediaType.APPLICATION_JSON)
          .build();
    } catch (Exception e) {
      throw new ModuleException("Error while generating the required image: " + data,
                                MuleChainErrorType.IMAGE_GENERATION_FAILURE,
                                e);
    }
  }

  /**
   * Reads scanned documents and converts to response as prompted by the user.
   * 
   * @param configuration Refers to the configuration object
   * @param data          Refers to the user prompt
   * @param filePath      Path to the file to be analyzed
   * @return Returns the list of analyzed pages of the document
   */
  @MediaType(value = APPLICATION_JSON, strict = false)
  @Alias("IMAGE-read-scanned-documents")
  @Throws(ImageErrorTypeProvider.class)
  @OutputJsonType(schema = "api/response/ScannedResponse.json")
  public org.mule.runtime.extension.api.runtime.operation.Result<InputStream, ScannedDocResponseAttributes> readScannedDocumentPDF(
                                                                                                                                   @Config LangchainLLMConfiguration configuration,
                                                                                                                                   @Content String data,
                                                                                                                                   String filePath) {

    LOGGER.debug("Image Read Scanned Documents Operation called with the prompt: {} & filePath: {}", data,
                 filePath);
    ChatLanguageModel model = configuration.getModel();

    JSONObject jsonObject = new JSONObject();
    JSONArray docPages = new JSONArray();

    int totalPages;
    List<ScannedDocResponseAttributes.DocResponseAttribute> docResponseAttributes = new ArrayList<>();

    try (InputStream inputStream = Files.newInputStream(Paths.get(filePath));
        PDDocument document = PDDocument.load(inputStream);) {

      PDFRenderer pdfRenderer = new PDFRenderer(document);
      totalPages = document.getNumberOfPages();
      LOGGER.info("Total files to be converted -> {}", totalPages);

      JSONObject docPage;

      for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {

        BufferedImage image = pdfRenderer.renderImageWithDPI(pageNumber, 300);
        LOGGER.debug("Reading page -> {}", pageNumber);

        String imageBase64 = convertToBase64String(image);
        UserMessage userMessage = UserMessage.from(
                                                   TextContent.from(data),
                                                   ImageContent.from(imageBase64, "image/png"));

        Response<AiMessage> response = model.generate(userMessage);

        // Calculate confidence score if enabled
        ConfidenceScore confidenceScore = null;
        if (configuration.getEnableConfidenceScore()) {
          try {
            confidenceScore = ConfidenceService.calculateConfidence(
                                                                    data, // Input prompt
                                                                    response.content().text(), // AI response
                                                                    configuration);
            LOGGER.debug("Confidence score calculated for page {}: {}", pageNumber + 1,
                         confidenceScore.getScore());
          } catch (Exception e) {
            LOGGER.warn("Failed to calculate confidence score for page {}: {}", pageNumber + 1,
                        e.getMessage());
          }
        }

        docPage = new JSONObject();
        docPage.put(MuleChainConstants.PAGE, pageNumber + 1);
        docPage.put(MuleChainConstants.RESPONSE, response.content().text());
        LOGGER.debug("Image Read Scanned Documents Operation completed with the response: {}",
                     response.content().text());

        // Create DocResponseAttribute with confidence score
        ScannedDocResponseAttributes.DocResponseAttribute docAttr;
        TokenUsage tokenUsage = new TokenUsage(response.tokenUsage().inputTokenCount(),
                                               response.tokenUsage().outputTokenCount(),
                                               response.tokenUsage().totalTokenCount());

        if (confidenceScore != null) {
          docAttr = new ScannedDocResponseAttributes.DocResponseAttribute(pageNumber + 1, tokenUsage,
                                                                          confidenceScore);
        } else {
          docAttr = new ScannedDocResponseAttributes.DocResponseAttribute(pageNumber + 1, tokenUsage);
        }

        docResponseAttributes.add(docAttr);
        docPages.put(docPage);
      }

    } catch (IOException e) {
      throw new ModuleException("Error occurred while processing the document file: " + filePath,
                                MuleChainErrorType.FILE_HANDLING_FAILURE, e);
    } catch (ModuleException e) {
      throw e;
    } catch (Exception e) {
      throw new ModuleException(
                                String.format("Unable to analyze the provided document %s with the text: %s", filePath,
                                              data),
                                MuleChainErrorType.IMAGE_ANALYSIS_FAILURE,
                                e);
    }

    jsonObject.put(MuleChainConstants.PAGES, docPages);

    Map<String, String> attributes = new HashMap<>();
    attributes.put(MuleChainConstants.TOTAL_PAGES, String.valueOf(totalPages));

    return createLLMResponse(jsonObject.toString(), docResponseAttributes,
                             attributes);
  }

  /**
   * Extracts specific fields from a scanned PDF document with confidence scores.
   * 
   * @param configuration   Refers to the configuration object
   * @param fieldsToExtract Comma-separated list of field names to extract (e.g.,
   *                        "name,address,phone")
   * @param filePath        Path to the PDF file to be analyzed
   * @return Returns the extracted fields with their confidence scores
   */
  @MediaType(value = APPLICATION_JSON, strict = false)
  @DisplayName("Extract fields with confidence score")
  @Summary("Extract specific fields from a scanned PDF document with confidence scores for each field")
  @Alias("IMAGE-extract-fields-with-confidence")
  @Throws(ImageErrorTypeProvider.class)
  @OutputJsonType(schema = "api/response/FieldExtractionResponse.json")
  public org.mule.runtime.extension.api.runtime.operation.Result<InputStream, FieldExtractionResponseAttributes> extractFieldsWithConfidence(
                                                                                                                                             @Config LangchainLLMConfiguration configuration,
                                                                                                                                             @DisplayName("Fields to extract") @Summary("Comma-separated list of field names to extract (e.g., 'name,address,phone')") @Content String fieldsToExtract,
                                                                                                                                             @DisplayName("PDF file path") @Summary("Path to the PDF file to be analyzed") String filePath,
                                                                                                                                             @DisplayName("Special instructions") @Summary("Additional instructions to be included with every field extraction query") @Optional String specialInstructions) {

    LOGGER.debug("Field Extraction Operation called with fields: {} & filePath: {}", fieldsToExtract, filePath);

    // Parse the comma-separated fields
    List<String> fields = new ArrayList<>(Arrays.asList(fieldsToExtract.split(",")));
    for (int i = 0; i < fields.size(); i++) {
      fields.set(i, fields.get(i).trim());
    }

    ChatLanguageModel model = configuration.getModel();

    JSONObject jsonObject = new JSONObject();
    JSONObject fieldsObject = new JSONObject();
    JSONObject summaryObject = new JSONObject();

    int totalPages;
    int totalFieldsFound = 0;
    double totalConfidenceSum = 0.0;
    int totalConfidenceCount = 0;

    // Create a thread pool for parallel field extraction
    ExecutorService executorService = Executors.newFixedThreadPool(Math.min(fields.size(), 10)); // Limit to 10
                                                                                                 // threads max

    try (InputStream inputStream = Files.newInputStream(Paths.get(filePath));
        PDDocument document = PDDocument.load(inputStream);) {

      PDFRenderer pdfRenderer = new PDFRenderer(document);
      totalPages = document.getNumberOfPages();
      LOGGER.info("Total pages to be processed for field extraction -> {}", totalPages);

      // Convert all pages to base64 images first
      List<String> pageImages = new ArrayList<>();
      for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
        BufferedImage image = pdfRenderer.renderImageWithDPI(pageNumber, 300);
        String imageBase64 = convertToBase64String(image);
        pageImages.add(imageBase64);
        LOGGER.debug("Converted page {} to base64", pageNumber + 1);
      }

      // Create a list of CompletableFuture for each field extraction task
      List<CompletableFuture<FieldExtractionResult>> fieldTasks = new ArrayList<>();

      // Submit each field extraction as a separate task
      for (String fieldName : fields) {
        CompletableFuture<FieldExtractionResult> task = CompletableFuture.supplyAsync(() -> {
          try {
            return extractFieldFromAllPages(fieldName, pageImages, model, configuration,
                                            specialInstructions);
          } catch (Exception e) {
            LOGGER.error("Error extracting field '{}': {}", fieldName, e.getMessage());
            return new FieldExtractionResult(fieldName, null, null, -1, null);
          }
        }, executorService);

        fieldTasks.add(task);
      }

      // Wait for all field extraction tasks to complete
      LOGGER.info("Waiting for all {} field extraction tasks to complete...", fields.size());
      CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                                                                 fieldTasks.toArray(new CompletableFuture[0]));

      // Block until all tasks are done
      allTasks.join();

      LOGGER.info("All field extraction tasks completed");

      // Collect results from all tasks
      for (CompletableFuture<FieldExtractionResult> task : fieldTasks) {
        FieldExtractionResult result = task.get();

        if (result.isSuccessful()) {
          JSONObject fieldObject = new JSONObject();
          fieldObject.put("value", result.getValue());
          fieldObject.put("page_number", result.getPageNumber());

          if (result.getConfidenceScore() != null) {
            fieldObject.put("confidence_score", result.getConfidenceScore().getScore());
            fieldObject.put("confidence_strategy", result.getConfidenceScore().getStrategy());
            if (result.getConfidenceScore().getMetrics() != null) {
              fieldObject.put("metrics", new JSONObject(result.getConfidenceScore().getMetrics()));
            }
            totalConfidenceSum += result.getConfidenceScore().getScore();
            totalConfidenceCount++;
          }

          fieldsObject.put(result.getFieldName(), fieldObject);
          totalFieldsFound++;

          LOGGER.debug("Field '{}' extracted successfully from page {}: {}",
                       result.getFieldName(), result.getPageNumber(), result.getValue());
        } else {
          LOGGER.debug("Field '{}' was not found in any page", result.getFieldName());
        }
      }

    } catch (IOException e) {
      throw new ModuleException("Error occurred while processing the document file: " + filePath,
                                MuleChainErrorType.FILE_HANDLING_FAILURE, e);
    } catch (ModuleException e) {
      throw e;
    } catch (Exception e) {
      throw new ModuleException(
                                String.format("Unable to extract fields from the provided document %s", filePath),
                                MuleChainErrorType.FIELD_EXTRACTION_FAILURE,
                                e);
    } finally {
      // Properly shutdown the executor service
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    // Build summary information
    summaryObject.put("total_fields_requested", fields.size());
    summaryObject.put("total_fields_found", totalFieldsFound);
    if (totalConfidenceCount > 0) {
      summaryObject.put("average_confidence", totalConfidenceSum / totalConfidenceCount);
    }

    jsonObject.put("fields", fieldsObject);
    jsonObject.put("total_pages", totalPages);
    jsonObject.put("extraction_summary", summaryObject);

    Map<String, String> attributes = new HashMap<>();
    attributes.put("total_pages", String.valueOf(totalPages));
    attributes.put("total_fields_requested", String.valueOf(fields.size()));
    attributes.put("total_fields_found", String.valueOf(totalFieldsFound));

    FieldExtractionResponseAttributes responseAttributes = new FieldExtractionResponseAttributes(totalPages,
                                                                                                 attributes);

    return Result.<InputStream, FieldExtractionResponseAttributes>builder()
        .output(toInputStream(jsonObject.toString(), StandardCharsets.UTF_8))
        .attributes(responseAttributes)
        .build();
  }

  /**
   * Helper class to hold field extraction results from multithreaded processing.
   */
  private static class FieldExtractionResult {

    private final String fieldName;
    private final String value;
    private final ConfidenceScore confidenceScore;
    private final int pageNumber;
    private final boolean successful;

    public FieldExtractionResult(String fieldName, String value, ConfidenceScore confidenceScore, int pageNumber,
                                 Boolean successful) {
      this.fieldName = fieldName;
      this.value = value;
      this.confidenceScore = confidenceScore;
      this.pageNumber = pageNumber;
      this.successful = successful != null ? successful
          : (value != null && !value.isEmpty() && !"NOT_FOUND".equalsIgnoreCase(value));
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

    public boolean isSuccessful() {
      return successful;
    }
  }

  /**
   * Extract a specific field from all pages of the document.
   */
  private FieldExtractionResult extractFieldFromAllPages(String fieldName, List<String> pageImages,
                                                         ChatLanguageModel model, LangchainLLMConfiguration configuration,
                                                         String specialInstructions) {

    for (int pageIndex = 0; pageIndex < pageImages.size(); pageIndex++) {
      try {
        String imageBase64 = pageImages.get(pageIndex);

        String extractionPrompt = String.format(
                                                "Please extract the value for the field '%s' from this document page. "
                                                    + "Return only the extracted value, or 'NOT_FOUND' if the field is not present on this page. "
                                                    + "Be precise and extract only the specific value requested.",
                                                fieldName);

        // Add special instructions if provided
        if (specialInstructions != null && !specialInstructions.trim().isEmpty()) {
          extractionPrompt += "\n\nSpecial instructions: " + specialInstructions.trim();
        }

        UserMessage userMessage = UserMessage.from(
                                                   TextContent.from(extractionPrompt),
                                                   ImageContent.from(imageBase64, "image/png"));

        Response<AiMessage> response = model.generate(userMessage);
        String extractedValue = response.content().text().trim();

        if (!"NOT_FOUND".equalsIgnoreCase(extractedValue) && !extractedValue.isEmpty()) {
          // Calculate confidence score if enabled
          ConfidenceScore confidenceScore = null;
          if (configuration.getEnableConfidenceScore()) {
            try {
              confidenceScore = ConfidenceService.calculateConfidence(
                                                                      extractionPrompt,
                                                                      extractedValue,
                                                                      configuration);
              LOGGER.debug("Confidence score calculated for field '{}' on page {}: {}",
                           fieldName, pageIndex + 1, confidenceScore.getScore());
            } catch (Exception e) {
              LOGGER.warn("Failed to calculate confidence score for field '{}' on page {}: {}",
                          fieldName, pageIndex + 1, e.getMessage());
            }
          }

          // Found the field, return the result
          return new FieldExtractionResult(fieldName, extractedValue, confidenceScore, pageIndex + 1, true);
        }

        LOGGER.debug("Field '{}' not found on page {}", fieldName, pageIndex + 1);

      } catch (Exception e) {
        LOGGER.error("Error processing field '{}' on page {}: {}", fieldName, pageIndex + 1, e.getMessage());
      }
    }

    // Field not found on any page
    LOGGER.debug("Field '{}' not found on any of the {} pages", fieldName, pageImages.size());
    return new FieldExtractionResult(fieldName, null, null, -1, false);
  }

  private String convertToBase64String(BufferedImage image) {
    String base64String;
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", outputStream);
      byte[] imageBytes = outputStream.toByteArray();
      base64String = Base64.getEncoder().encodeToString(imageBytes);
      return base64String;
    } catch (IOException e) {
      throw new ModuleException("Error occurred while processing the image",
                                MuleChainErrorType.IMAGE_PROCESSING_FAILURE, e);
    }
  }

  private boolean isURL(String fileNameFilter) {
    String urlPattern = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$";
    return fileNameFilter.matches(urlPattern);
  }

  private static String convertToBase64String(String filePath) {
    try {
      // Read file bytes
      byte[] fileContent = Files.readAllBytes(new File(filePath).toPath());
      // Encode bytes to Base64
      return Base64.getEncoder().encodeToString(fileContent);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
}
