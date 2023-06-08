package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.wso2.micro.integrator.grpc.proto.MIServiceGrpc;

public class TheDummyClass {
    private MIServiceGrpc.MIServiceStub stub;
    public TheDummyClass() {
        initializeGrpcClient();
    }
    public void initializeGrpcClient(){
        System.out.println("Initializing gRPC client");

        Runnable task = () -> {
            try {
                System.out.println("Starting gRPC client");
                startGrpcClient();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    private void startGrpcClient() throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();
        //stub = MIServiceGrpc.newStub(channel);
        System.out.println("dataExchange");
        //dataExchange();
    }
}
