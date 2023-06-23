package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

public class ResourceNotFoundException extends Exception{
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
