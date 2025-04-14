package com.workflow;
import io.serverlessworkflow.api.Workflow;
import io.serverlessworkflow.api.functions.FunctionDefinition;
import io.serverlessworkflow.api.interfaces.WorkflowValidator;
import io.serverlessworkflow.api.states.DefaultState.Type;
import io.serverlessworkflow.api.states.OperationState;
import io.serverlessworkflow.validation.WorkflowValidatorImpl;
import org.rocksdb.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Simple RocksDB wrapper for serverless workflow operations
 */
public class RocksDBService {
    private static final String DB_PATH = "rocksdb-data";
    private static RocksDB db;

    static {
        try {
            RocksDB.loadLibrary();
            Files.createDirectories(Path.of(DB_PATH));

            try (Options options = new Options()) {
                options.setCreateIfMissing(true);
                db = RocksDB.open(options, DB_PATH);
                System.out.println("RocksDB initialized successfully");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RocksDB", e);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (db != null) {
                db.close();
                System.out.println("RocksDB closed successfully");
            }
        }));
    }

    public ObjectNode put(ObjectNode input) {
        try {
            String key = input.get("key").asText();
            String value = input.get("value").asText();

            db.put(key.getBytes(StandardCharsets.UTF_8),
                    value.getBytes(StandardCharsets.UTF_8));

            ObjectNode result = new ObjectMapper().createObjectNode();
            result.put("success", true);
            result.put("message", "Value stored successfully");
            return result;
        } catch (Exception e) {
            ObjectNode result = new ObjectMapper().createObjectNode();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    public ObjectNode get(ObjectNode input) {
        try {
            String key = input.get("key").asText();
            byte[] valueBytes = db.get(key.getBytes(StandardCharsets.UTF_8));

            ObjectNode result = new ObjectMapper().createObjectNode();

            if (valueBytes != null) {
                String value = new String(valueBytes, StandardCharsets.UTF_8);
                result.put("success", true);
                result.put("value", value);
            } else {
                result.put("success", false);
                result.put("message", "Key not found: " + key);
            }

            return result;
        } catch (Exception e) {
            ObjectNode result = new ObjectMapper().createObjectNode();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    public ObjectNode delete(ObjectNode input) {
        try {
            String key = input.get("key").asText();
            db.delete(key.getBytes(StandardCharsets.UTF_8));

            ObjectNode result = new ObjectMapper().createObjectNode();
            result.put("success", true);
            result.put("message", "Key deleted successfully");
            return result;
        } catch (Exception e) {
            ObjectNode result = new ObjectMapper().createObjectNode();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}