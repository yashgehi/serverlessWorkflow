package com.workflow;

import org.rocksdb.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Improved RocksDB wrapper for serverless workflow operations
 */
public class RocksDBService {
    private static final Logger LOGGER = Logger.getLogger(RocksDBService.class.getName());
    private static final String DB_PATH = "rocksdb-data";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static RocksDB db;
    private static boolean initialized = false;

    static {
        initializeRocksDB();
    }

    /**
     * Initialize RocksDB instance safely
     */
    private static synchronized void initializeRocksDB() {
        if (initialized) return;

        try {
            RocksDB.loadLibrary();
            Files.createDirectories(Path.of(DB_PATH));

            Options options = new Options();
            options.setCreateIfMissing(true);

            // Add optimal configuration for our use case
            options.setIncreaseParallelism(Runtime.getRuntime().availableProcessors());
            options.optimizeLevelStyleCompaction();

            db = RocksDB.open(options, DB_PATH);
            LOGGER.info("RocksDB initialized successfully");

            // Register shutdown hook for cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                closeDatabase();
            }));

            initialized = true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize RocksDB", e);
            throw new RuntimeException("Failed to initialize RocksDB", e);
        }
    }

    /**
     * Close the database safely
     */
    private static synchronized void closeDatabase() {
        if (db != null) {
            db.close();
            LOGGER.info("RocksDB closed successfully");
            db = null;
            initialized = false;
        }
    }

    /**
     * Store a key-value pair in RocksDB
     */
    public ObjectNode put(ObjectNode input) {
        String key = null;
        try {
            ensureInitialized();

            key = input.get("key").asText();
            String value = input.get("value").asText();

            db.put(key.getBytes(StandardCharsets.UTF_8),
                    value.getBytes(StandardCharsets.UTF_8));

            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", true);
            result.put("message", "Value stored successfully");
            return result;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error storing key: " + key, e);

            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Retrieve a value by key from RocksDB
     */
    public ObjectNode get(ObjectNode input) {
        String key = null;
        try {
            ensureInitialized();

            key = input.get("key").asText();
            byte[] valueBytes = db.get(key.getBytes(StandardCharsets.UTF_8));

            ObjectNode result = MAPPER.createObjectNode();

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
            LOGGER.log(Level.WARNING, "Error retrieving key: " + key, e);

            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Delete a key-value pair from RocksDB
     */
    public ObjectNode delete(ObjectNode input) {
        String key = null;
        try {
            ensureInitialized();

            key = input.get("key").asText();
            db.delete(key.getBytes(StandardCharsets.UTF_8));

            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", true);
            result.put("message", "Key deleted successfully");
            return result;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error deleting key: " + key, e);

            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Ensure database is initialized
     */
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (RocksDBService.class) {
                if (!initialized) {
                    initializeRocksDB();
                }
            }
        }
    }
}