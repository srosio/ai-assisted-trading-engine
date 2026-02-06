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

            // Check if this is an EventBridge scheduled event (autonomous scanner trigger)
            if (eventNode.has("source") && eventNode.get("source").asText().startsWith("aws.")) {
                log.info("EventBridge scheduled event detected - routing to scanMarkets");
                // Wrap in API Gateway-like format for FunctionInvoker
                ObjectNode wrappedEvent = objectMapper.createObjectNode();
                ObjectNode headers = objectMapper.createObjectNode();
                headers.put("spring.cloud.function.definition", "scanMarkets");
                wrappedEvent.set("headers", headers);
                wrappedEvent.put("body", "{}");
                inputBytes = objectMapper.writeValueAsBytes(wrappedEvent);
            }
            // Check if this is API Gateway v2.0 format and extract routeKey
            else if (eventNode.has("version") && "2.0".equals(eventNode.get("version").asText())) {
                String routeKey = eventNode.has("routeKey") ? eventNode.get("routeKey").asText() : null;

                if (routeKey != null) {
                    log.info("API Gateway v2.0 event detected. RouteKey: {}", routeKey);

                    // Determine function name based on routeKey
                    String functionName = determineFunctionName(routeKey);
                    log.info("Mapped routeKey '{}' to function '{}'", routeKey, functionName);

                    // Set spring.cloud.function.definition header to directly specify the function
                    if (eventNode.has("headers")) {
                        ((ObjectNode) eventNode.get("headers")).put("spring.cloud.function.definition", functionName);
                    } else {
                        ObjectNode newHeaders = objectMapper.createObjectNode();
                        newHeaders.put("spring.cloud.function.definition", functionName);
                        ((ObjectNode) eventNode).set("headers", newHeaders);
                    }

                    // Convert back to input stream with modified event
                    inputBytes = objectMapper.writeValueAsBytes(eventNode);
                    log.debug("Enhanced event with function definition header");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse/enhance API Gateway event: {}. Proceeding with original input.", e.getMessage());
        }

        // Pass to FunctionInvoker with potentially modified input
        invoker.handleRequest(new ByteArrayInputStream(inputBytes), output, context);
    }

    /**
     * Maps API Gateway routeKey to Spring Cloud Function name
     */
    private String determineFunctionName(String routeKey) {
        if (routeKey == null) {
            return "processWebhook"; // default
        }

        return switch (routeKey) {
            case "POST /api/webhook/tradingview" -> "processWebhook";
            case "GET /api/journal/statistics" -> "getStatistics";
            case "GET /api/journal" -> "getJournalEntries";
            case "POST /api/scan", "POST /api/scan/markets" -> "scanMarkets";
            default -> {
                log.warn("Unknown routeKey: {}. Defaulting to processWebhook", routeKey);
                yield "processWebhook";
            }
        };
    }
}
