package com.fixmate.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;
@Configuration
public class GeminiConfig {
    @Bean
    RestClient geminiRestClient(RestClient.Builder builder,
            @Value("${app.gemini.connect-timeout-ms:3000}") long connect,
            @Value("${app.gemini.read-timeout-ms:15000}") long read) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connect));
        factory.setReadTimeout(Duration.ofMillis(read));
        return builder.baseUrl("https://generativelanguage.googleapis.com/v1beta").requestFactory(factory).build();
    }
}
