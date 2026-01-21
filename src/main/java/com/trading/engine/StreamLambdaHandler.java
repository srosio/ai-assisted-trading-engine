package com.trading.engine;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import org.springframework.cloud.function.adapter.aws.FunctionInvoker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Entry point for AWS Lambda.
 * Point your Lambda Handler to: com.trading.engine.StreamLambdaHandler
 * 
 * This class delegates to Spring Cloud Function's FunctionInvoker.
 * We can add custom SnapStart hooks here using org.crac.Resource if needed in
 * the future.
 */
public class StreamLambdaHandler implements RequestStreamHandler {

    private final FunctionInvoker invoker;

    public StreamLambdaHandler() {
        this.invoker = new FunctionInvoker();
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        invoker.handleRequest(input, output, context);
    }
}
