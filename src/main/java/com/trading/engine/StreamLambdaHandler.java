package com.trading.engine;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.function.adapter.aws.FunctionInvoker;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamLambdaHandler implements RequestStreamHandler {

    private static ConfigurableApplicationContext applicationContext;
    private final FunctionInvoker invoker;

    public StreamLambdaHandler() {
        if (applicationContext == null) {
            applicationContext = SpringApplication.run(TradingEngineApplication.class);
        }
        this.invoker = new FunctionInvoker();
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        invoker.handleRequest(input, output, context);
    }
}
