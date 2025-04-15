package com.workflow;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.servlet.ServletContainer;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RestServerApplication {
    private static final Logger LOGGER = Logger.getLogger(RestServerApplication.class.getName());
    private static final int PORT = 8080;

    public static void main(String[] args) {
        configureLogging();
        LOGGER.info("Starting REST server application...");

        Server server = createServer();

        // Load existing workflows from RocksDB
        WorkflowManager.loadExistingWorkflows();

        startServer(server);
    }

    private static void configureLogging() {
        // Enable detailed Jersey logging
        Logger jerseyLogger = Logger.getLogger("org.glassfish.jersey");
        jerseyLogger.setLevel(Level.FINE);

        // Enable Jetty debug logging
        Logger jettyLogger = Logger.getLogger("org.eclipse.jetty");
        jettyLogger.setLevel(Level.FINE);
    }

    private static Server createServer() {
        // Create and configure Jetty server
        Server server = new Server(PORT);
        LOGGER.info("Created Jetty server on port " + PORT);

        // Create context handler
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        LOGGER.info("Created ServletContextHandler");

        // Create and configure Jersey servlet
        ServletHolder jerseyServlet = configureJerseyServlet();

        // Add Jersey servlet to context
        context.addServlet(jerseyServlet, "/*");
        LOGGER.info("Added Jersey servlet to context");

        // Set handler on server
        server.setHandler(context);
        LOGGER.info("Set context as server handler");

        return server;
    }

    private static ServletHolder configureJerseyServlet() {
        ServletHolder jerseyServlet = new ServletHolder(new ServletContainer());
        LOGGER.info("Created Jersey ServletContainer");

        // Use the Application class approach for resource registration
        jerseyServlet.setInitParameter("javax.ws.rs.Application",
                "com.workflow.WorkflowApplication");

        // Enable JSON processing
        jerseyServlet.setInitParameter("jersey.config.server.provider.classnames",
                JacksonFeature.class.getName());

        // Enable detailed request/response logging
        jerseyServlet.setInitParameter(
                "jersey.config.server.tracing", "ALL");
        jerseyServlet.setInitParameter(
                "jersey.config.server.tracing.threshold", "VERBOSE");

        LOGGER.info("Configured Jersey parameters");
        return jerseyServlet;
    }

    private static void startServer(Server server) {
        try {
            LOGGER.info("Starting server...");
            server.start();

            logServerInfo();

            // Wait for server to finish
            server.join();
        } catch (Exception ex) {
            LOGGER.severe("Error starting server: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            server.destroy();
        }
    }

    private static void logServerInfo() {
        LOGGER.info("Server started at http://localhost:" + PORT + "/");
        LOGGER.info("Workflow endpoints available at:");
        LOGGER.info("- GET    http://localhost:" + PORT + "/workflows/test");
        LOGGER.info("- GET    http://localhost:" + PORT + "/workflows/health");
        LOGGER.info("- POST   http://localhost:" + PORT + "/workflows/register");
        LOGGER.info("- POST   http://localhost:" + PORT + "/workflows/execute/{workflowId}");
    }
}