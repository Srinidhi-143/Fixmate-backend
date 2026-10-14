package com.fixmate.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Contract tests with explicit fixtures; these do not claim a live Gemini response. */
class GeminiClientTest {
    ObjectMapper mapper = new ObjectMapper();
    MockRestServiceServer server;
    RestClient restClient;
    GeminiClient client;
    List<String> categories = List.of("Plumbing", "Electrical", "Laptop Repair", "Other");
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = new GeminiClient(restClient, mapper, "test-key", "test-model", "gemini-3.6-flash");
    }
    Map<String,Object> fixture(boolean diy) {
        var result = new LinkedHashMap<String,Object>();
        result.put("problemSummary", "A tap connection needs checking");
        result.put("category", "Plumbing"); result.put("serviceCategory", "Plumbing");
        result.put("possibleCauses", List.of("A loose external connection"));
        result.put("troubleshootingSteps", List.of("Check the visible connection without disassembly"));
        result.put("canSelfFix", diy); result.put("technicianRequired", !diy);
        result.put("severity", "LOW"); result.put("safetyWarning", "Stop if conditions are unsafe"); result.put("confidence", 0.8);
        return result;
    }
    String envelope(Map<String,Object> result) throws Exception {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP", "content", Map.of("parts", List.of(Map.of("text", mapper.writeValueAsString(result))))))));
    }
    void respond(Map<String,Object> result) throws Exception {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/test-model:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.generationConfig.responseFormat.text.mimeType").value("APPLICATION_JSON"))
                .andRespond(withSuccess(envelope(result), MediaType.APPLICATION_JSON));
    }
    @Test void validStructuredDiyResponseUsesPrimaryWithoutFallback() throws Exception {
        respond(fixture(true)); var result = client.analyze("Tap is dripping", "Other", categories);
        assertTrue(result.canSelfFix()); assertFalse(result.technicianRequired()); assertEquals("Plumbing", result.predictedCategory()); server.verify();
    }
    @Test void validTechnicianResponse() throws Exception {
        respond(fixture(false)); assertTrue(client.analyze("Pipe has a major leak", "Other", categories).technicianRequired());
    }
    @Test void hazardOverridesUnsafeDiyAndSteps() throws Exception {
        var result = fixture(true); result.put("troubleshootingSteps", List.of("Open the live electrical panel")); respond(result);
        var safe = client.analyze("Electrical socket is sparking", "Electrical", categories);
        assertFalse(safe.canSelfFix()); assertTrue(safe.technicianRequired());
        assertFalse(safe.troubleshootingSteps().toString().contains("Open the live"));
    }
    @Test void unknownCategoryRejected() throws Exception {
        var result = fixture(true); result.put("category", "Invented category"); respond(result);
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap problem", "Other", categories));
    }
    @Test void missingRequiredFieldsBecomeUnavailable() throws Exception {
        for (String field : fixture(true).keySet()) {
            server.reset();
            var result = fixture(true); result.remove(field); respond(result);
            assertThrows(GeminiClient.AIServiceUnavailableException.class,
                    () -> client.analyze("Tap problem", "Other", categories), "Missing field: " + field);
            server.verify();
        }
    }
    @Test void swollenBatteryNeverReturnsUnsafeRepairSteps() throws Exception {
        var result = fixture(true);
        result.put("category", "Laptop Repair"); result.put("serviceCategory", "Laptop Repair");
        result.put("troubleshootingSteps", List.of("Open the case and puncture the battery")); respond(result);
        var safe = client.analyze("My laptop battery is swollen and the bottom case is bulging.", "Other", categories);
        assertTrue(safe.technicianRequired()); assertFalse(safe.canSelfFix());
        assertEquals("Laptop Repair", safe.predictedCategory());
        assertEquals(List.of("Stop using the affected item and keep a safe distance.",
                "Contact a qualified technician; seek local emergency help if there is immediate danger."), safe.troubleshootingSteps());
        server.verify();
    }
    @Test void contradictoryDecisionRejected() throws Exception {
        var result = fixture(true); result.put("technicianRequired", true); respond(result);
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap problem", "Other", categories));
    }
    @Test void malformedJsonRejected() {
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiClient.class);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        try {
            server.expect(anything()).andRespond(withSuccess("{malformed", MediaType.APPLICATION_JSON));
            assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap problem", "Other", categories));
            assertTrue(logs.list.get(0).getFormattedMessage().contains("Gemini response parsing failure"));
        } finally { logger.detachAppender(logs); logs.stop(); }
    }
    @Test void missingKeyFailsWithoutNetworkCall() {
        var absent = new GeminiClient(RestClient.create(), mapper, "", "test-model", "gemini-3.6-flash");
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> absent.analyze("Tap", "Other", categories)); server.verify();
    }
    @Test void invalidKeyHandled() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
    }
    @Test void rateLimitHandled() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(fallbackUrl())).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
    }
    @Test void serverErrorHandled() {
        server.expect(anything()).andRespond(withServerError());
        server.expect(requestTo(fallbackUrl())).andRespond(withServerError());
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
    }
    @Test void timeoutHandled() {
        server.expect(anything()).andRespond(request -> { throw new java.net.SocketTimeoutException("test timeout"); });
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
    }
    @Test void imageIsSentInlineToGemini() throws Exception {
        server.expect(anything()).andExpect(jsonPath("$.contents[0].parts[1].inline_data.mime_type").value("image/png"))
                .andExpect(jsonPath("$.contents[0].parts[1].inline_data.data").value("AQID"))
                .andRespond(withSuccess(envelope(fixture(false)), MediaType.APPLICATION_JSON));
        assertEquals("ANALYZED", client.analyzeImage("Tap problem", "Other", categories, new ByteArrayResource(new byte[]{1,2,3}), "image/png").imageAnalysisStatus());
    }

    @Test void mildLaptopWarmthCanRemainDiy() throws Exception {
        var result = fixture(true);
        result.put("category", "Laptop Repair"); result.put("serviceCategory", "Laptop Repair");
        result.put("problemSummary", "Slight warmth during normal laptop use");
        result.put("troubleshootingSteps", List.of("Close unused browser tabs", "Use a hard flat surface and keep vents clear"));
        respond(result);
        assertTrue(client.analyze("My laptop becomes slightly warm when I use many browser tabs, but otherwise it works normally.", "Other", categories).canSelfFix());
    }

    @Test void aliasesResolveToExistingCategoryAndMediumToNormal() throws Exception {
        var result = fixture(false);
        result.put("category", "Computer Repair"); result.put("serviceCategory", " laptop service ");
        result.put("severity", "MEDIUM"); respond(result);
        var analysis = client.analyze("Laptop will not start", "Other", categories);
        assertEquals("Laptop Repair", analysis.predictedCategory());
        assertEquals("Laptop Repair", analysis.recommendedService()); assertEquals("NORMAL", analysis.priority());
    }

    @Test void wholeJsonCodeFenceAccepted() throws Exception {
        respondText("```json\n" + mapper.writeValueAsString(fixture(true)) + "\n```");
        assertTrue(client.analyze("Tap is dripping", "Other", categories).canSelfFix());
    }

    @Test void trailingContentAndDuplicateFieldsRejected() throws Exception {
        for (String json : List.of(mapper.writeValueAsString(fixture(true)) + " {}",
                mapper.writeValueAsString(fixture(true)).replace("\"canSelfFix\":true", "\"canSelfFix\":true,\"canSelfFix\":false"),
                "Explanation\n```json\n" + mapper.writeValueAsString(fixture(true)) + "\n```")) {
            server.reset(); respondText(json);
            assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
        }
    }

    @Test void dangerousAdviceOverridesBenignDescriptionAndSanitizesAllAdviceFields() throws Exception {
        var result = fixture(true);
        result.put("problemSummary", "Open the powered equipment to repair it");
        result.put("possibleCauses", List.of("Remove the battery and puncture it"));
        result.put("troubleshootingSteps", List.of("Bypass the safety switch")); respond(result);
        var analysis = client.analyze("An item does not work", "Other", categories);
        assertTrue(analysis.technicianRequired()); assertFalse(analysis.canSelfFix());
        assertFalse(analysis.possibleProblem().contains("Open"));
        assertFalse(analysis.possibleCauses().toString().contains("puncture"));
        assertFalse(analysis.troubleshootingSteps().toString().contains("Bypass"));
    }

    @Test void nullAndWrongFieldTypesRejected() throws Exception {
        for (Object value : Arrays.asList(null, "true", 1)) {
            server.reset(); var result = fixture(true); result.put("canSelfFix", value); respond(result);
            assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
        }
    }

    @Test void incompleteCandidateRejected() throws Exception {
        server.expect(anything()).andRespond(withSuccess(envelope(fixture(true)).replace("STOP", "MAX_TOKENS"), MediaType.APPLICATION_JSON));
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
    }

    @Test void providerStatusModelAndUsefulMessageLoggedWithoutKeysOrRawHeaders() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiClient.class);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        try {
            String anotherKey = "AIza" + "A".repeat(35);
            String message = "Unsupported response schema; key test-key and " + anotherKey + "\nshould be redacted";
            String body = mapper.writeValueAsString(Map.of("error", Map.of("code", 400, "status", "INVALID_ARGUMENT", "message", message)));
            server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body));
            var exception = assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
            assertEquals("Gemini is temporarily unavailable", exception.getMessage()); assertNull(exception.getCause());
            String logged = logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.contains("Gemini provider failure")).findFirst().orElseThrow();
            assertTrue(logged.contains("model=test-model")); assertTrue(logged.contains("httpStatus=400"));
            assertTrue(logged.contains("providerCode=400")); assertTrue(logged.contains("providerStatus=INVALID_ARGUMENT"));
            assertTrue(logged.contains("Unsupported response schema")); assertTrue(logged.contains("[REDACTED]"));
            assertFalse(logged.contains("test-key")); assertFalse(logged.contains(anotherKey));
            assertFalse(logged.contains("x-goog-api-key")); assertFalse(logged.contains("\n"));
        } finally { logger.detachAppender(logs); logs.stop(); }
    }

    @Test void eachProviderHttpFailureKeepsExactStatusAndCleanCustomerException() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiClient.class);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        try {
            for (HttpStatus status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND,
                    HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR)) {
                server.reset(); logs.list.clear();
                String body = mapper.writeValueAsString(Map.of("error", Map.of("code", status.value(), "status", status.name(), "message", "Provider diagnostic")));
                server.expect(anything()).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
                if (status == HttpStatus.INTERNAL_SERVER_ERROR || status == HttpStatus.TOO_MANY_REQUESTS)
                    server.expect(requestTo(fallbackUrl())).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
                var exception = assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
                assertEquals("Gemini is temporarily unavailable", exception.getMessage());
                assertTrue(logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains("httpStatus=" + status.value())));
                assertTrue(logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains("providerStatus=" + status.name())));
                server.verify();
            }
        } finally { logger.detachAppender(logs); logs.stop(); }
    }

    @Test void unreadableProviderBodyIsNeverCopiedIntoLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiClient.class);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        try {
            server.expect(anything()).andRespond(withServerError().body("<html>test-key private request body</html>"));
            server.expect(requestTo(fallbackUrl())).andRespond(withServerError().body("<html>test-key private request body</html>"));
            assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
            String logged = logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.contains("Gemini provider failure")).findFirst().orElseThrow();
            assertTrue(logged.contains("httpStatus=500")); assertTrue(logged.contains("unreadable error body"));
            assertFalse(logged.contains("test-key")); assertFalse(logged.contains("private request body"));
        } finally { logger.detachAppender(logs); logs.stop(); }
    }

    String fallbackUrl() { return "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent"; }

    void productionModels() {
        client = new GeminiClient(restClient, mapper, "test-key", "gemini-3.8-flash", "gemini-3.6-flash");
    }

    String primaryUrl() { return "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"; }

    Map<String, Object> laptopFixture(boolean diy) {
        var result = fixture(diy);
        result.put("category", "Laptop Repair"); result.put("serviceCategory", "Laptop Repair");
        result.put("problemSummary", "Slight laptop warmth during normal use");
        result.put("possibleCauses", List.of("Normal processor workload"));
        result.put("troubleshootingSteps", List.of("Close unused browser tabs", "Use a hard flat surface and keep vents clear"));
        return result;
    }

    @Test void primary503FallsBackOnceWithIdenticalStructuredRequest() throws Exception {
        productionModels();
        var bodies = new ArrayList<String>();
        server.expect(requestTo(primaryUrl())).andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(request -> bodies.add(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(fallbackUrl())).andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(request -> bodies.add(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess(envelope(laptopFixture(true)), MediaType.APPLICATION_JSON));
        var result = client.analyze("My laptop becomes slightly warm while using many browser tabs, but otherwise it works normally.", "Other", categories);
        assertTrue(result.canSelfFix()); assertFalse(result.technicianRequired());
        assertEquals("Laptop Repair", result.recommendedService());
        assertEquals(2, bodies.size()); assertEquals(mapper.readTree(bodies.get(0)), mapper.readTree(bodies.get(1)));
        server.verify();
    }

    @Test void primary429FallsBackAndLogsActualSuccessfulModel() throws Exception {
        productionModels();
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiClient.class);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        try {
            server.expect(requestTo(primaryUrl())).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
            server.expect(requestTo(fallbackUrl())).andRespond(withSuccess(envelope(laptopFixture(true)), MediaType.APPLICATION_JSON));
            assertTrue(client.analyze("Slight laptop warmth", "Other", categories).canSelfFix());
            String logged = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
            assertTrue(logged.contains("Gemini primary failure: model=gemini-3.8-flash, status=429"));
            assertTrue(logged.contains("Gemini fallback attempt: model=gemini-3.6-flash"));
            assertTrue(logged.contains("Gemini fallback success: model=gemini-3.6-flash"));
            assertFalse(logged.contains("test-key")); server.verify();
        } finally { logger.detachAppender(logs); logs.stop(); }
    }

    @Test void otherAllowedTransientStatusesUseOneFallback() throws Exception {
        productionModels();
        for (HttpStatus status : List.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY, HttpStatus.GATEWAY_TIMEOUT)) {
            server.reset();
            server.expect(requestTo(primaryUrl())).andRespond(withStatus(status));
            server.expect(requestTo(fallbackUrl())).andRespond(withSuccess(envelope(laptopFixture(true)), MediaType.APPLICATION_JSON));
            assertTrue(client.analyze("Slight laptop warmth", "Other", categories).canSelfFix()); server.verify();
        }
    }

    @Test void authPermissionRequestAndValidationFailuresNeverFallBack() {
        productionModels();
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND, HttpStatus.UNPROCESSABLE_ENTITY)) {
            server.reset(); server.expect(requestTo(primaryUrl())).andRespond(withStatus(status));
            assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Laptop issue", "Other", categories));
            server.verify(); // Any fallback would be an unexpected request and fail the test.
        }
    }

    @Test void explicitPermissionErrorWrappedIn503DoesNotFallBack() throws Exception {
        productionModels();
        server.expect(requestTo(primaryUrl())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(Map.of("error", Map.of("code", 403, "status", "PERMISSION_DENIED", "message", "Denied")))));
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Laptop issue", "Other", categories));
        server.verify();
    }

    @Test void bothModelsFailWithCleanExceptionAndNoThirdAttempt() {
        productionModels();
        server.expect(requestTo(primaryUrl())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(fallbackUrl())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("Private provider error"));
        var error = assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Laptop issue", "Other", categories));
        assertEquals("Gemini is temporarily unavailable", error.getMessage()); assertNull(error.getCause()); server.verify();
    }

    @Test void malformedFallbackIsFailureRatherThanSuccessfulAnalysis() throws Exception {
        productionModels(); var result = laptopFixture(true); result.put("technicianRequired", true);
        server.expect(requestTo(primaryUrl())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(fallbackUrl())).andRespond(withSuccess(envelope(result), MediaType.APPLICATION_JSON));
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Laptop issue", "Other", categories));
        server.verify();
    }

    @Test void swollenBatterySafetyOverridesUnsafeFallbackAdvice() throws Exception {
        productionModels(); var result = laptopFixture(true);
        result.put("troubleshootingSteps", List.of("Open the battery and puncture it"));
        server.expect(requestTo(primaryUrl())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(fallbackUrl())).andRespond(withSuccess(envelope(result), MediaType.APPLICATION_JSON));
        var safe = client.analyze("My laptop battery is swollen and the bottom case is bulging.", "Other", categories);
        assertTrue(safe.technicianRequired()); assertFalse(safe.canSelfFix()); assertEquals("HIGH", safe.priority());
        assertEquals("Laptop Repair", safe.recommendedService());
        assertEquals(List.of("Stop using the affected item and keep a safe distance.",
                "Contact a qualified technician; seek local emergency help if there is immediate danger."), safe.troubleshootingSteps());
        server.verify();
    }

    @Test void imageInputIsPreservedOnFallback() throws Exception {
        productionModels();
        server.expect(requestTo(primaryUrl())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(fallbackUrl())).andExpect(jsonPath("$.contents[0].parts[1].inline_data.data").value("AQID"))
                .andRespond(withSuccess(envelope(laptopFixture(false)), MediaType.APPLICATION_JSON));
        assertEquals("ANALYZED", client.analyzeImage("Laptop issue", "Other", categories, new ByteArrayResource(new byte[]{1,2,3}), "image/png").imageAnalysisStatus());
        server.verify();
    }

    void respondText(String text) throws Exception {
        var body = Map.of("candidates", List.of(Map.of("finishReason", "STOP", "content", Map.of("parts", List.of(Map.of("text", text))))));
        server.expect(anything()).andRespond(withSuccess(mapper.writeValueAsString(body), MediaType.APPLICATION_JSON));
    }
}
