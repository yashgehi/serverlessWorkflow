package com.workflow;

import javax.ws.rs.ApplicationPath;
import org.glassfish.jersey.jackson.JacksonFeature;
import java.util.HashSet;
import java.util.Set;

@ApplicationPath("/")
public class WorkflowApplication extends javax.ws.rs.core.Application {
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> resources = new HashSet<>();
        // Register resource classes
        resources.add(WorkflowResource.class);
        // Register JSON processing
        resources.add(JacksonFeature.class);
        return resources;
    }
}