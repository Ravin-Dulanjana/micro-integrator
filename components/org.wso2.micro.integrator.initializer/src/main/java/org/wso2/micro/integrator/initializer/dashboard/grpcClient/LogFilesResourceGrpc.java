package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.OMText;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.transport.passthru.util.RelayConstants;
import org.apache.synapse.transport.passthru.util.StreamingOnRequestDataSource;
import org.wso2.micro.integrator.grpc.proto.Message;

import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class LogFilesResourceGrpc {

    private static final Log log = LogFactory.getLog(LogFilesResourceGrpc.class);
    public static org.wso2.micro.integrator.grpc.proto.LogFileList populateLogFileInfo() {
        List<LogFileInfo> logFileInfoList = GrpcUtils.getLogFileInfoList();
        return setResponseBody(logFileInfoList);
    }

    private static List<LogFileInfo> getSearchResults(String searchKey) {

        return GrpcUtils.getLogFileInfoList().stream()
                .filter(resource -> resource.getLogName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    public static org.wso2.micro.integrator.grpc.proto.LogFileList populateSearchResults(String searchKey) {

        List<LogFileInfo> searchResultList = getSearchResults(searchKey);
        return setResponseBody(searchResultList);
    }

    private static org.wso2.micro.integrator.grpc.proto.LogFileList setResponseBody(Collection<LogFileInfo> logFileInfoList) {

        org.wso2.micro.integrator.grpc.proto.LogFileList.Builder logFileListBuilder =
                org.wso2.micro.integrator.grpc.proto.LogFileList.newBuilder().setCount(logFileInfoList.size());

        for (LogFileInfo logFileInfo : logFileInfoList) {
            org.wso2.micro.integrator.grpc.proto.LogFile.Builder logFileBuilder =
                    org.wso2.micro.integrator.grpc.proto.LogFile.newBuilder();
            logFileBuilder.setName(logFileInfo.getLogName());
            logFileBuilder.setSize(logFileInfo.getFileSize());
            logFileListBuilder.addLogFiles(logFileBuilder.build());
        }
        return logFileListBuilder.build();
//        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
//        axis2MessageContext.removeProperty(NO_ENTITY_BODY);
    }

    // not finalized(need to check after proper implementation)
    public static Message populateFileContent(String pathParameter) {

        DataHandler dataHandler = downloadArchivedLogFiles(pathParameter);
        if (dataHandler != null) {
            try {
                InputStream fileInput = dataHandler.getInputStream();
                SOAPFactory factory = OMAbstractFactory.getSOAP12Factory();
                SOAPEnvelope env = factory.getDefaultEnvelope();
                OMNamespace ns =
                        factory.createOMNamespace(RelayConstants.BINARY_CONTENT_QNAME.getNamespaceURI(), "ns");
                OMElement omEle = factory.createOMElement(RelayConstants.BINARY_CONTENT_QNAME.getLocalPart(), ns);
                StreamingOnRequestDataSource ds = new StreamingOnRequestDataSource(fileInput);
                dataHandler = new DataHandler(ds);
                OMText textData = factory.createOMText(dataHandler, true);
                omEle.addChild(textData);
                env.getBody().addChild(omEle);
//                synCtx.setEnvelope(env);
//                axis2MessageContext.removeProperty(NO_ENTITY_BODY);
//                axis2MessageContext.setProperty(Constants.MESSAGE_TYPE, "application/octet-stream");
//                axis2MessageContext.setProperty(Constants.CONTENT_TYPE, "application/txt");
            } catch (AxisFault e) {
                log.error("Error occurred while creating the response", e);
//                sendFaultResponse(axis2MessageContext);
            } catch (IOException e) {
                log.error("Error occurred while reading the input stream", e);
//                sendFaultResponse(axis2MessageContext);
            }
        } else {
//            sendFaultResponse(axis2MessageContext);
        }
        return Message.newBuilder().setMessage("File " + pathParameter + " downloaded").build();
    }

    private static DataHandler downloadArchivedLogFiles(String logFile) {

        ByteArrayDataSource bytArrayDS;
        Path logFilePath = Paths.get(GrpcUtils.getCarbonLogsPath(), logFile);

        File file = new File(logFilePath.toString());
        if (file.exists() && !file.isDirectory()) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(logFilePath.toString()))) {
                bytArrayDS = new ByteArrayDataSource(is, "text/xml");
                return new DataHandler(bytArrayDS);
            } catch (FileNotFoundException e) {
                log.error("Could not find the requested file : " + logFile + " in : " + logFilePath.toString(), e);
                return null;
            } catch (IOException e) {
                log.error("Error occurred while reading the file : " + logFile + " in : " + logFilePath.toString(), e);
                return null;
            }
        } else {
            log.error("Could not find the requested file : " + logFile + " in : " + logFilePath.toString());
            return null;
        }
    }
}
