package com.workflow;

import io.serverlessworkflow.api.Workflow;
import io.serverlessworkflow.api.functions.FunctionDefinition;
import io.serverlessworkflow.api.interfaces.WorkflowValidator;
import io.serverlessworkflow.api.states.DefaultState.Type;
import io.serverlessworkflow.api.states.OperationState;
import io.serverlessworkflow.validation.WorkflowValidatorImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Core executor for ServerlessWorkflow with RocksDB operations
 */
public class ServerlessWorkflowRocksDB {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Workflow> workflowRegistry = new ConcurrentHashMap<>();
    private static final Map<String, Function<ObjectNode, ObjectNode>> functionRegistry = new HashMap<>();
    private static final RocksDBService rocksDBService = new RocksDBService();

    static {
        // Register RocksDB functions
        functionRegistry.put("put", rocksDBService::put);
        functionRegistry.put("get", rocksDBService::get);
        functionRegistry.put("delete", rocksDBService::delete);
    }

    /**
     * Register a workflow with the executor
     */
    public static void registerWorkflow(String workflowId, String workflowJson) {
        try {
            // Parse workflow JSON to workflow object
            Workflow workflow = Workflow.fromSource(workflowJson);

            // Validate workflow
            WorkflowValidator validator = new WorkflowValidatorImpl();
            validator.setWorkflow(workflow);

            if (!validator.isValid()) {
//                throw new IllegalArgumentException("Invalid workflow: " +
//                        String.join(", ", validator.getValidationErrors()));
            }

            // Register workflow
            workflowRegistry.put(workflowId, workflow);
            System.out.println("Workflow registered: " + workflowId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register workflow: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a workflow with the given input
     */
    public static JsonNode executeWorkflow(String workflowId, JsonNode input) {
        try {
            Workflow workflow = workflowRegistry.get(workflowId);
            if (workflow == null) {
                throw new IllegalArgumentException("Workflow not found: " + workflowId);
            }

            // Create execution context
            String executionId = UUID.randomUUID().toString();
            ObjectNode context = MAPPER.createObjectNode();
            context.set("input", input);
            context.put("executionId", executionId);

            // Get start state
            String startStateName = workflow.getStart().getStateName();

            // Execute workflow starting from start state
            return executeState(workflow, startStateName, context);
        } catch (Exception e) {
            ObjectNode errorResult = MAPPER.createObjectNode();
            errorResult.put("error", "Workflow execution failed");
            errorResult.put("message", e.getMessage());
            return errorResult;
        }
    }

    /**
     * Execute a specific state in the workflow
     */
    private static JsonNode executeState(Workflow workflow, String stateName, ObjectNode context) {
        var state = workflow.getStates().stream()
                .filter(s -> s.getName().equals(stateName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("State not found: " + stateName));

        // Execute based on state type
        if (state.getType() == Type.OPERATION) {
            OperationState opState = (OperationState) state;

            // Execute each action in the operation state
            opState.getActions().forEach(action -> {
                String functionName = action.getFunctionRef().getRefName();

                // Find the function definition
                // Assuming workflow.getFunctions() returns a List or similar
                List<FunctionDefinition> functionDefinitions = workflow.getFunctions().getFunctionDefs();

                FunctionDefinition functionDef = functionDefinitions.stream()
                        .filter(f -> f.getName().equals(functionName)) // Replace with appropriate accessor
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Function not found: " + functionName));



                // Get the operation name from function definition
                String operation = functionDef.getOperation();

                // Execute the function
                Function<ObjectNode, ObjectNode> function = functionRegistry.get(operation);
                if (function == null) {
                    throw new IllegalArgumentException("Function not registered: " + operation);
                }

                // Extract arguments from context
                ObjectNode args = context.deepCopy();
                if (action.getFunctionRef().getArguments() != null) {
                    args.setAll((ObjectNode) action.getFunctionRef().getArguments());
                }

                // Execute function and update context with result
                ObjectNode result = function.apply(args);
                context.set("result", result);
            });

            // Transition to next state or end
            if (opState.getTransition() != null) {
                return executeState(workflow, opState.getTransition().getNextState(), context);
            }
        }

        // Return the final context
        return context;
    }
}