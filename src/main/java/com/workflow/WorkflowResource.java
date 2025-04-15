package com.workflow;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST API endpoints for workflow operations
 */
@Path("/workflows")
@Produces(MediaType.APPLICATION_JSON)
public class WorkflowResource {
    private static final Logger LOGGER = Logger.getLogger(WorkflowResource.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Simple test endpoint to verify connectivity
     */
    @GET
    @Path("/test")
    @Produces(MediaType.TEXT_PLAIN)
    public Response test() {
        LOGGER.info("Test endpoint called");
        return Response.ok("API is working!").build();
    }

    /**
     * Health check endpoint
     */
    @GET
    @Path("/health")
    public Response healthCheck() {
        LOGGER.info("Health check endpoint called");

        ObjectNode response = MAPPER.createObjectNode();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());

        // Check RocksDB connectivity
        try {
            RocksDBService rocksDBService = new RocksDBService();
            ObjectNode testRequest = MAPPER.createObjectNode();
            testRequest.put("key", "health-check");
            rocksDBService.get(testRequest);
            response.put("database", "connected");
        } catch (Exception e) {
            response.put("database", "error: " + e.getMessage());
            LOGGER.warning("Health check database error: " + e.getMessage());
        }

        return Response.ok(response).build();
    }

    /**
     * Register a new workflow
     */
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registerWorkflow(String requestBody) {
        LOGGER.info("Register workflow endpoint called");

        try {
            JsonNode requestJson = MAPPER.readTree(requestBody);

            // Extract workflow ID and definition
            String workflowId = requestJson.get("workflowId").asText();
            String workflowDefinition = requestJson.get("workflow").toString();

            LOGGER.info("Registering workflow: " + workflowId);

            // Register workflow with the executor
            ServerlessWorkflowRocksDB.registerWorkflow(workflowId, workflowDefinition);

            // Store workflow in RocksDB for persistence
            WorkflowManager.persistWorkflow(workflowId, workflowDefinition);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("status", "success");
            response.put("message", "Workflow registered successfully");
            return Response.status(Response.Status.CREATED).entity(response).build();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error registering workflow", e);

            ObjectNode errorResponse = MAPPER.createObjectNode();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(errorResponse).build();
        }
    }

    /**
     * Execute a workflow with provided parameters
     */
    @POST
    @Path("/execute/{workflowId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response executeWorkflow(@PathParam("workflowId") String workflowId, String requestBody) {
        LOGGER.info("Execute workflow endpoint called for: " + workflowId);

        try {
            // Parse input parameters
            JsonNode input = MAPPER.readTree(requestBody);

            // If the workflow isn't registered yet, try to load it from RocksDB
            ensureWorkflowIsLoaded(workflowId);

            // Process special functions like API calls
            JsonNode processedInput = processAPICallsInInput(input);

            // Execute the workflow
            LOGGER.info("Executing workflow: " + workflowId);
            JsonNode result = ServerlessWorkflowRocksDB.executeWorkflow(workflowId, processedInput);

            return Response.ok(result).build();

        } catch (IllegalArgumentException e) {
            LOGGER.warning("Workflow execution error: " + e.getMessage());

            ObjectNode errorResponse = MAPPER.createObjectNode();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(errorResponse).build();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing workflow", e);

            ObjectNode errorResponse = MAPPER.createObjectNode();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
        }
    }

    /**
     * Ensure the workflow is loaded before execution
     */
    private void ensureWorkflowIsLoaded(String workflowId) {
        // Check if workflow is already registered
        if (!isWorkflowRegistered(workflowId)) {
            LOGGER.info("Workflow not in memory, loading from DB: " + workflowId);

            // Try to load from DB
            String workflowDefinition = WorkflowManager.loadWorkflow(workflowId);

            if (workflowDefinition == null) {
                throw new IllegalArgumentException("Workflow not found: " + workflowId);
            }

            // Register the loaded workflow
            ServerlessWorkflowRocksDB.registerWorkflow(workflowId, workflowDefinition);
        }
    }

    /**
     * Check if a workflow is registered in memory
     */
    private boolean isWorkflowRegistered(String workflowId) {
        try {
            // This should be replaced with a proper check against the registry
            // For now, we're using the same approach as in the original code
            String workflowDefinition = WorkflowManager.loadWorkflow(workflowId);
            return workflowDefinition != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Process any API calls in the input and include the results
     */
    private JsonNode processAPICallsInInput(JsonNode input) throws IOException, InterruptedException {
        ObjectNode processedInput = input.deepCopy();

        // Check if there are any API calls to make
        if (input.has("apiCalls") && input.get("apiCalls").isArray()) {
            LOGGER.info("Processing API calls in workflow input");

            for (JsonNode apiCall : input.get("apiCalls")) {
                processApiCall(processedInput, apiCall);
            }
        }

        return processedInput;
    }

    /**
     * Process a single API call from the input
     */
    private void processApiCall(ObjectNode processedInput, JsonNode apiCall)
            throws IOException, InterruptedException {

        String apiId = apiCall.get("id").asText();
        String url = apiCall.get("url").asText();
        String method = apiCall.has("method") ? apiCall.get("method").asText() : "GET";
        String resultKey = apiCall.get("resultKey").asText();

        LOGGER.info("Making API call: " + apiId + " to " + url + " with method " + method);

        // Make the API call
        JsonNode apiResult = makeApiCall(method, url,
                apiCall.has("body") ? apiCall.get("body") : null);

        // Store the result in the input under the specified key
        processedInput.set(resultKey, apiResult);
    }

    /**
     * Make an API call and return the result
     */
    private JsonNode makeApiCall(String method, String url, JsonNode body)
            throws IOException, InterruptedException {

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");

        HttpRequest request = buildHttpRequest(method, requestBuilder, body);
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        LOGGER.info("API call response status: " + response.statusCode());

        // Parse response
        return MAPPER.readTree(response.body());
    }

    /**
     * Build the HTTP request based on the method
     */
    private HttpRequest buildHttpRequest(String method, HttpRequest.Builder builder, JsonNode body) {
        String bodyString = body != null ? body.toString() : "{}";

        switch (method.toUpperCase()) {
            case "POST":
                return builder.POST(HttpRequest.BodyPublishers.ofString(bodyString)).build();
            case "PUT":
                return builder.PUT(HttpRequest.BodyPublishers.ofString(bodyString)).build();
            case "DELETE":
                return builder.DELETE().build();
            case "GET":
            default:
                return builder.GET().build();
        }
    }
}