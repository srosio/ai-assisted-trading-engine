package com.trading.engine;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.function.adapter.aws.FunctionInvoker;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
public class StreamLambdaHandler implements RequestStreamHandler {

    private final FunctionInvoker invoker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StreamLambdaHandler() {
        this.invoker = new FunctionInvoker();
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        // Read the input stream
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        byte[] inputBytes = buffer.toByteArray();

        try {
            // Parse the API Gateway event
            JsonNode eventNode = objectMapper.readTree(inputBytes);

            // Check if this is API Gateway v2.0 format and extract routeKey
            if (eventNode.has("version") && "2.0".equals(eventNode.get("version").asText())) {
                String routeKey = eventNode.has("routeKey") ? eventNode.get("routeKey").asText() : null;

                if (routeKey != null) {
                    log.info("API Gateway v2.0 event detected. RouteKey: {}", routeKey);

                    // Add routeKey to headers for Spring Cloud Function routing
                    if (eventNode.has("headers")) {
                        ((ObjectNode) eventNode.get("headers")).put("routeKey", routeKey);
                    } else {
                        ObjectNode headers = objectMapper.createObjectNode();
                        headers.put("routeKey", routeKey);
                        ((ObjectNode) eventNode).set("headers", headers);
                    }

                    // Convert back to input stream with modified event
                    inputBytes = objectMapper.writeValueAsBytes(eventNode);
                    log.debug("Enhanced event with routeKey header");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse/enhance API Gateway event: {}. Proceeding with original input.", e.getMessage());
        }

        // Pass to FunctionInvoker with potentially modified input
        invoker.handleRequest(new ByteArrayInputStream(inputBytes), output, context);
    }
}
