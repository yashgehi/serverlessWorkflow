package com.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * REST endpoints for workflow operations
 * This class provides a REST interface to register and execute workflows
 */
public class WorkflowResource {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Register a new workflow
     *
     * @param workflowId Unique identifier for the workflow
     * @param workflowJson JSON definition of the workflow
     * @return Result of the registration operation
     */
    public ObjectNode registerWorkflow(String workflowId, String workflowJson) {
        try {
            ServerlessWorkflowRocksDB.registerWorkflow(workflowId, workflowJson);

            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", true);
            result.put("message", "Workflow registered successfully");
            return result;
        } catch (Exception e) {
            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Execute a workflow with the given input
     *
     * @param workflowId ID of the workflow to execute
     * @param inputJson JSON input for the workflow
     * @return Result of the workflow execution
     */
    public JsonNode executeWorkflow(String workflowId, String inputJson) {
        try {
            JsonNode input = MAPPER.readTree(inputJson);
            return ServerlessWorkflowRocksDB.executeWorkflow(workflowId, input);
        } catch (Exception e) {
            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}