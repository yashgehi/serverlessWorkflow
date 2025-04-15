//package com.workflow;
//
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
///**
// * Main application class to demonstrate usage of the ServerlessWorkflowRocksDB
// */
//public class Application {
//    private static final ObjectMapper MAPPER = new ObjectMapper();
//
//    public static void main(String[] args) throws Exception {
//        // Example workflow definition
//        String workflowJson = """
//        {
//          "id": "simple_rocksdb_workflow",
//          "version": "1.0",
//          "name": "Simple RocksDB Operation Workflow",
//          "description": "A workflow that performs a single RocksDB operation",
//          "start": {
//            "stateName": "PerformOperation"
//          },
//          "functions": [
//            {
//              "name": "rocksDbOperation",
//              "operation": "put"
//            }
//          ],
//          "states": [
//            {
//              "name": "PerformOperation",
//              "type": "operation",
//              "actions": [
//                {
//                  "name": "singleAction",
//                  "functionRef": {
//                    "refName": "rocksDbOperation",
//                    "arguments": {
//                      "key": "${$.key}",
//                      "value": "${$.value}"
//                    }
//                  }
//                }
//              ],
//              "end": true
//            }
//          ]
//        }
//        """;
//
//        // Register the workflow
//        ServerlessWorkflowRocksDB.registerWorkflow("simple_rocksdb", workflowJson);
//
//        // Create test input
//        String inputJson = """
//        {
//          "key": "customer:123",
//          "value": "John Doe"
//        }
//        """;
//        JsonNode input = MAPPER.readTree(inputJson);
//
//        // Execute workflow
//        JsonNode result = ServerlessWorkflowRocksDB.executeWorkflow("simple_rocksdb", input);
//        System.out.println("Workflow execution result: " + MAPPER.writeValueAsString(result));
//
//        // Demonstrate using the WorkflowResource class
//        WorkflowResource resource = new WorkflowResource();
//
//        // Try a get operation with a different workflow
//        String getWorkflowJson = """
//        {
//          "id": "get_workflow",
//          "version": "1.0",
//          "name": "RocksDB Get Operation",
//          "start": {
//            "stateName": "GetOperation"
//          },
//          "functions": [
//            {
//              "name": "getFunction",
//              "operation": "get"
//            }
//          ],
//          "states": [
//            {
//              "name": "GetOperation",
//              "type": "operation",
//              "actions": [
//                {
//                  "name": "getAction",
//                  "functionRef": {
//                    "refName": "getFunction",
//                    "arguments": {
//                      "key": "${$.key}"
//                    }
//                  }
//                }
//              ],
//              "end": true
//            }
//          ]
//        }
//        """;
//
//        // Register and execute the get workflow
//        resource.registerWorkflow("get_operation", getWorkflowJson);
//        JsonNode getResult = resource.executeWorkflow("get_operation", """
//        {
//          "key": "customer:123"
//        }
//        """);
//
//        System.out.println("Get operation result: " + MAPPER.writeValueAsString(getResult));
//    }
//}