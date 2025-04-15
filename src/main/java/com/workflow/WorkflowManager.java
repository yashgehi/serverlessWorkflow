package com.workflow;

import org.rocksdb.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Manages workflow loading and persistence operations
 */
public class WorkflowManager {
    private static final Logger LOGGER = Logger.getLogger(WorkflowManager.class.getName());
    private static final String WORKFLOW_KEY_PREFIX = "workflow:";

    /**
     * Load existing workflows from RocksDB and register them with the ServerlessWorkflowRocksDB
     */
    public static void loadExistingWorkflows() {
        LOGGER.info("Loading existing workflows from RocksDB...");

        try (Options options = new Options()) {
            options.setCreateIfMissing(true);

            // Try-with-resources for proper resource management
            try (RocksDB db = RocksDB.open(options, "rocksdb-data");
                 RocksIterator iterator = db.newIterator()) {

                int loadedCount = 0;
                for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    String key = new String(iterator.key(), StandardCharsets.UTF_8);

                    // Check if the key represents a workflow
                    if (key.startsWith(WORKFLOW_KEY_PREFIX)) {
                        processWorkflowFromDB(key, iterator.value());
                        loadedCount++;
                    }
                }

                LOGGER.info("Loaded " + loadedCount + " workflows from RocksDB.");
            }
        } catch (Exception e) {
            LOGGER.severe("Error loading workflows: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processWorkflowFromDB(String key, byte[] valueBytes) {
        try {
            String workflowId = key.substring(WORKFLOW_KEY_PREFIX.length());
            String workflowDefinition = new String(valueBytes, StandardCharsets.UTF_8);

            // Register the workflow
            ServerlessWorkflowRocksDB.registerWorkflow(workflowId, workflowDefinition);
            LOGGER.info("Loaded and registered workflow: " + workflowId);
        } catch (Exception e) {
            String workflowId = key.substring(WORKFLOW_KEY_PREFIX.length());
            LOGGER.warning("Error registering workflow " + workflowId + ": " + e.getMessage());
        }
    }

    /**
     * Store a workflow in RocksDB
     */
    public static void persistWorkflow(String workflowId, String workflowDefinition) {
        try {
            RocksDBService service = new RocksDBService();
            String key = WORKFLOW_KEY_PREFIX + workflowId;

            com.fasterxml.jackson.databind.node.ObjectNode input =
                    new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            input.put("key", key);
            input.put("value", workflowDefinition);

            service.put(input);
            LOGGER.info("Persisted workflow: " + workflowId);
        } catch (Exception e) {
            LOGGER.severe("Error persisting workflow " + workflowId + ": " + e.getMessage());
            throw new RuntimeException("Failed to persist workflow", e);
        }
    }

    /**
     * Load a workflow from RocksDB
     */
    public static String loadWorkflow(String workflowId) {
        try {
            RocksDBService service = new RocksDBService();
            String key = WORKFLOW_KEY_PREFIX + workflowId;

            com.fasterxml.jackson.databind.node.ObjectNode input =
                    new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            input.put("key", key);

            com.fasterxml.jackson.databind.node.ObjectNode result = service.get(input);

            if (result.get("success").asBoolean()) {
                return result.get("value").asText();
            } else {
                return null;
            }
        } catch (Exception e) {
            LOGGER.warning("Error loading workflow " + workflowId + ": " + e.getMessage());
            return null;
        }
    }
}