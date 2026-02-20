package com.translation.agent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SpringBootApplication
@RestController
@RequestMapping("/rest")
public class TranslationAgent {

	private static final Logger logger = LogManager.getLogger(TranslationAgent.class);

	private AnthropicClient client;
	private String sourceSchema;
	private String targetSchema;
	private String mappingRules;

	@Value("${app.claude.api.key}")
	private String apiKey;

	private final Map<String, StoredAlgorithm> algorithmStorage = new ConcurrentHashMap<>();

	/**
	 * Stored algorithm with metadata
	 */
	public static class StoredAlgorithm {
		public String id;
		public String name;
		public String description;
		public String algorithm; // Java code as string
		public Map<String, FieldTransform> fieldRules;
		public LocalDateTime createdAt;
		public LocalDateTime lastUsed;
		public long usageCount;
		public String createdBy;
		public String status;

		public StoredAlgorithm(String id, String name) {
			this.id = id;
			this.name = name;
			this.createdAt = LocalDateTime.now();
			this.usageCount = 0;
			this.status = "ACTIVE";
		}
	}

	/**
	 * Field transformation rule
	 */
	public static class FieldTransform {
		public String sourceField;
		public String targetField;
		public String transformation; // direct, remove, multiply, etc
		public String dataType;
		public Object defaultValue;

		public FieldTransform(String src, String tgt, String trans) {
			this.sourceField = src;
			this.targetField = tgt;
			this.transformation = trans;
		}
	}

	public TranslationAgent() {		
		loadResources();
	}

	/**
	 * Load schemas and mappings
	 */
	private void loadResources() {
		try {
			this.sourceSchema = loadResourceAsString("source-schema.json");
			this.targetSchema = loadResourceAsString("target-schema.json");
			this.mappingRules = loadResourceAsString("mappings.csv");
			logger.info("Resources loaded\n");
		} catch (Exception e) {
			logger.error("Error loading resources: " + e.getMessage(), e);
		}
	}

	/**
	 * Load resource file
	 */
	private String loadResourceAsString(String resourceName) throws Exception {
		InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
		if (inputStream != null) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}
		throw new Exception("Resource not found: " + resourceName);
	}

	/**
	 * Extract text from message
	 */
	private String extractTextFromMessage(Message message) {
		for (ContentBlock content : message.content()) {
			if (content.getClass().getSimpleName().equals("TextBlock")) {
				TextBlock textBlock = content.asText();
				return textBlock.text();
			}
		}
		return "";
	}

	/**
	 * This is needed only time we call Claude with schema/mapping
	 */
	@PostMapping("/admin/download-algorithm")
	public ResponseEntity<?> downloadAlgorithm(@RequestParam String algorithmName,
			@RequestParam(defaultValue = "admin") String user) {

		this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
		
		try {
			logger.info("ALGORITHM DOWNLOAD");
			String algorithmId = generateAlgorithmId(algorithmName);

			logger.info("1) Generating algorithm ID: " + algorithmId);
			logger.info("2) Algorithm Name: " + algorithmName);
			logger.info("3) Downloaded by: " + user);

			// Build prompt with ONLY schema/mapping info (NO client data)
			String prompt = "Generate a transformation algorithm that converts data from "
					+ "source schema to target schema using these mappings.\n\n"
					+ "Return ONLY executable Java code (no explanation):\n\n" + "SOURCE SCHEMA:\n" + sourceSchema
					+ "\n\n" + "TARGET SCHEMA:\n" + targetSchema + "\n\n" + "FIELD MAPPINGS:\n" + mappingRules + "\n\n"
					+ "Requirements:\n"
					+ "1. Create a static method: public static Map<String, Object> transform(Map<String, Object> source)\n"
					+ "2. Apply all field mappings\n"
					+ "3. Handle transformations (rename, type convert, remove chars, multiply, conditional, etc)\n"
					+ "4. Return transformed Map matching target schema\n" + "5. Handle null values gracefully\n"
					+ "6. Pure Java, no dependencies, no external calls\n" + "7. Include error handling\n";

			logger.info("Calling Claude to generate algorithm...\n");

			MessageCreateParams params = MessageCreateParams.builder().model(Model.CLAUDE_OPUS_4_6).maxTokens(4096L)
					.addUserMessage(prompt).build();

			Message message = client.messages().create(params);
			String algorithmCode = extractTextFromMessage(message);

			logger.info("Algorithm generated (" + algorithmCode.length() + " chars)\n");

			// Parse field mappings
			Map<String, FieldTransform> fieldRules = parseFieldMappings();

			// Create and store algorithm
			StoredAlgorithm algorithm = new StoredAlgorithm(algorithmId, algorithmName);
			algorithm.algorithm = algorithmCode;
			algorithm.fieldRules = fieldRules;
			algorithm.createdBy = user;

			algorithmStorage.put(algorithmId, algorithm);

			logger.info("Algorithm stored with ID: " + algorithmId);

			return ResponseEntity.ok(Map.of("input", prompt, "algorithmId", algorithmId, "algorithm", fieldRules));

		} catch (Exception e) {
			logger.error("Error downloading algorithm: " + e.getMessage(), e);
			return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
		}
	}

	/**
	 * Generate algorithm ID from name
	 */
	private String generateAlgorithmId(String name) {
		return "ALGO_" + name.toUpperCase().replaceAll("\\s+", "_") + "_" + System.currentTimeMillis();
	}

	/**
	 * Parse field mappings from CSV
	 */
	private Map<String, FieldTransform> parseFieldMappings() {
		Map<String, FieldTransform> rules = new LinkedHashMap<>();
		String[] lines = mappingRules.split("\n");

		for (int i = 1; i < lines.length; i++) {
			String[] parts = lines[i].split(",");
			if (parts.length >= 3) {
				String source = parts[0].trim();
				String target = parts[1].trim();
				String transform = parts[2].trim();

				FieldTransform rule = new FieldTransform(source, target, transform);
				if (parts.length > 3) {
					rule.dataType = parts[3].trim();
				}
				rules.put(source, rule);
			}
		}

		return rules;
	}

	/**
	 * POST /transform - Transformer API Uses stored algorithm to transform data
	 * Client data NEVER sent to Claude source-schema.json to target-schema.json
	 */
	@PostMapping("/transform")
	public ResponseEntity<?> transform(@RequestBody Map<String, Object> sourceData,
			@RequestParam(defaultValue = "latest") String algorithmId,
			@RequestParam(defaultValue = "client") String user) {

		try {
			long startTime = System.currentTimeMillis();

			logger.info("Begin Transformation");

			// Get latest algorithm if "latest" specified
			if ("latest".equals(algorithmId)) {
				algorithmId = algorithmStorage.keySet().stream().max(String::compareTo)
						.orElseThrow(() -> new Exception("No algorithm found. Please download first."));
			}

			logger.info("Using algorithm: " + algorithmId);

			// Get stored algorithm
			StoredAlgorithm algorithm = algorithmStorage.get(algorithmId);
			if (algorithm == null) {
				throw new Exception("Algorithm not found: " + algorithmId);
			}

			logger.info("Algorithm name: " + algorithm.name);
			logger.info("Executing transformation locally");
			logger.info("Input data: " + sourceData);

			// Execute transformation using stored algorithm rules
			Map<String, Object> targetData = executeAlgorithmLocally(algorithm, sourceData);

			long executionTime = System.currentTimeMillis() - startTime;

			logger.info("Transformation complete (" + executionTime + "ms)");
			logger.info("Output data: " + targetData);

			// Update algorithm usage
			algorithm.usageCount++;
			algorithm.lastUsed = LocalDateTime.now();

			logger.info("TRANSFORMATION COMPLETE\n");

			return ResponseEntity.ok(new ObjectMapper().writeValueAsString(targetData));

		} catch (Exception e) {
			logger.error("Error during transformation: " + e.getMessage(), e);
			return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
		}
	}

	/**
	 * Execute algorithm locally (NO EXTERNAL CALLS)
	 */
	private Map<String, Object> executeAlgorithmLocally(StoredAlgorithm algorithm, Map<String, Object> sourceData) {

		Map<String, Object> targetData = new HashMap<>();

		// Apply each field transformation rule
		for (Map.Entry<String, FieldTransform> entry : algorithm.fieldRules.entrySet()) {
			String sourceField = entry.getKey();
			FieldTransform rule = entry.getValue();

			Object sourceValue = sourceData.get(sourceField);
			Object targetValue = applyTransformation(sourceValue, rule);

			targetData.put(rule.targetField, targetValue);
		}

		return targetData;
	}

	/**
	 * Apply transformation rule to a value
	 */
	private Object applyTransformation(Object value, FieldTransform rule) {
		if (value == null) {
			return rule.defaultValue;
		}

		String trans = rule.transformation.toLowerCase();
		String strValue = value.toString();

		if (trans.contains("direct")) {
			return value;
		} else if (trans.contains("remove") && trans.contains("hyphen")) {
			return strValue.replaceAll("-", "");
		} else if (trans.contains("uppercase")) {
			return strValue.toUpperCase();
		} else if (trans.contains("lowercase")) {
			return strValue.toLowerCase();
		} else if (trans.contains("multiply") && trans.contains("by")) {
			try {
				double factor = Double.parseDouble(trans.replaceAll("[^0-9.]", ""));
				return Double.parseDouble(strValue) * factor;
			} catch (Exception e) {
				return value;
			}
		} else if (trans.contains("if") && trans.contains("→")) {
			boolean boolValue = Boolean.parseBoolean(strValue);
			return boolValue ? "ACTIVE" : "INACTIVE";
		}

		return value;
	}

	/**
	 * GET /admin/algorithms List stored algorithms
	 */
	@GetMapping("/admin/algorithms")
	public ResponseEntity<?> listAlgorithms() {
		List<Map<String, Object>> algorithms = new ArrayList<>();

		for (StoredAlgorithm algo : algorithmStorage.values()) {
			algorithms.add(Map.of("id", algo.id, "name", algo.name, "createdAt", algo.createdAt.toString(), "createdBy",
					algo.createdBy, "usageCount", algo.usageCount, "lastUsed",
					algo.lastUsed != null ? algo.lastUsed.toString() : "Never", "status", algo.status));
		}

		return ResponseEntity.ok(Map.of("count", algorithms.size(), "algorithms", algorithms));
	}

}
