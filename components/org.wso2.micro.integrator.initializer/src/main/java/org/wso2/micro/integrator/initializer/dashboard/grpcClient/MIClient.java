package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.wso2.micro.integrator.grpc.proto.*;

import java.util.concurrent.TimeUnit;

public class MIClient {
    private MIServiceGrpc.MIServiceStub stub;
    StreamObserver<DataRequest> requestObserver;
    private final String nodeID = "dev_Node";
    private final String groupID = "dev_Grp";
    //Hard-coded
    ServerInfo serverInfo = ServerInfo.newBuilder()
            .setProductVersion("4.2.0-alpha")
            .setOsVersion("10.0")
            .setJavaVersion("11.0.18")
            .setCarbonHome("C:\\Users\\RAVINF~1\\WSO2\\Github\\Builds\\WSO2MI~1.0-S\\bin\\..")
            .setJavaVendor("OpenLogic")
            .setOsName("Windows 10")
            .setProductName("WSO2 Micro Integrator")
            .setJavaHome("C:\\Program Files\\OpenJDK\\jdk-11.0.18.10-hotspot").build();

    public static void main(String[] args) {
        MIClient miClient = new MIClient();
    }
    public MIClient() {
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
        stub = MIServiceGrpc.newStub(channel);
        dataExchange();
    }

    public void dataExchange() throws InterruptedException {
        StreamObserver<DataResponse> responseObserver = new StreamObserver<DataResponse>() {
            @Override
            public void onNext(DataResponse dataResponse) {
                int responseType = dataResponse.getResponseType().getNumber();
                if (responseType == 0){
                    requestObserver.onNext(DataRequest.newBuilder().setServerInfo(serverInfo).build());
                } else if (responseType == 1) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setApiList(ApiResourceGrpc.populateApiList()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setApiList(ApiResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 2) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setApi(ApiResourceGrpc.populateGrpcApiData(response)).build());
                }  else if (responseType == 3) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setEndpointList(EndpointResourceGrpc.populateEndpointList()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setEndpointList(EndpointResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 4) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setEndpoint(EndpointResourceGrpc.populateEndpointData(response)).build());
                } else if (responseType == 5) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setCarbonAppList(CarbonAppResourceGrpc.populateCarbonAppList()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setCarbonAppList(CarbonAppResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 6) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setCarbonApp(CarbonAppResourceGrpc.populateGrpcCarbonAppData(response)).build());
                } else if (responseType == 7) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setConnectorList(ConnectorResourceGrpc.populateConnectorList()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setConnectorList(ConnectorResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 8) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setConnector(ConnectorResourceGrpc.populateConnectorData(response)).build());
                } else if (responseType == 9) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setDataServiceList(DataServiceResourceGrpc.populateDataServiceList()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setDataServiceList(DataServiceResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 10) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setDataService(DataServiceResourceGrpc.populateDataServiceByName(response)).build());
                }else if (responseType == 11) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setInboundEndpointList(InboundEndpointResourceGrpc.populateInboundEndpointList()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setInboundEndpointList(InboundEndpointResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 12) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setInboundEndpoint(InboundEndpointResourceGrpc.populateInboundEndpointData(response)).build());
                } else if (responseType == 13) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setLogFileList(LogFilesResourceGrpc.populateLogFileInfo()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setLogFileList(LogFilesResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 14) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setMessage(LogFilesResourceGrpc.populateFileContent(response)).build());
                } else if (responseType == 15) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setLogConfigList(LoggingResourceGrpc.getAllLoggerDetails()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setLogConfigList(LoggingResourceGrpc.getAllLoggerDetails(response)).build());
                    }
                } else if (responseType == 16) {
                    String loggerName = dataResponse.getUpdateConfigLog().getLoggerName();
                    String loggerClass = dataResponse.getUpdateConfigLog().getLoggerClass();
                    String logLevel = dataResponse.getUpdateConfigLog().getLogLevel();
                    if (loggerClass != null) {
                        requestObserver.onNext(DataRequest.newBuilder().setMessage(LoggingResourceGrpc.updateLoggerData(loggerName, loggerClass, logLevel)).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setMessage(LoggingResourceGrpc.updateLoggerData(loggerName, logLevel)).build());
                    }
                }else if (responseType == 17) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setMessageProcessorsList(MessageProcessorResourceGrpc.populateMessageProcessorList()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setMessageProcessorsList(MessageProcessorResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 18) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setMessageProcessor(MessageProcessorResourceGrpc.populateMessageProcessorData(response)).build());
                } else if (responseType == 19) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setMessageStoreList(MessageStoreResourceGrpc.populateMessageStoreList()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setMessageStoreList(MessageStoreResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 20) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setMessageStore(MessageStoreResourceGrpc.populateMessageStoreData(response)).build());
                } else if (responseType == 21) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setProxyServiceList(ProxyServiceResourceGrpc.populateProxyServiceList()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setProxyServiceList(ProxyServiceResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 22) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setProxyService(ProxyServiceResourceGrpc.populateProxyServiceData(response)).build());
                } else if (responseType == 25) {
                    String response = dataResponse.getResponse();
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setSequenceList(SequenceResourceGrpc.populateSequenceList()).build());
                    } else {
                        requestObserver.onNext(DataRequest.newBuilder().setSequenceList(SequenceResourceGrpc.populateSearchResults(response)).build());
                    }
                } else if (responseType == 26) {
                    String response = dataResponse.getResponse();
                    requestObserver.onNext(DataRequest.newBuilder().setSequence(SequenceResourceGrpc.populateSequenceData(response)).build());
                }

            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void onCompleted() {
                System.out.println("Client completed!");
            }

        };


        requestObserver = stub.dataExchange(responseObserver);

        Handshake handshake = Handshake.newBuilder().setNodeID(nodeID).setGroupID(groupID).build();
        DataRequest request = DataRequest.newBuilder().setHandshake(handshake).build();
        requestObserver.onNext(request);

        while(true){
            TimeUnit.SECONDS.sleep(5);
        }
//        requestObserver.onCompleted();


    }
}