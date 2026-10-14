package com.fixmate.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Contract tests with explicit fixtures; these do not claim a live Gemini response. */
class GeminiClientTest {
    ObjectMapper mapper = new ObjectMapper();
    MockRestServiceServer server;
    GeminiClient client;
    List<String> categories = List.of("Plumbing", "Electrical", "Laptop Repair", "Other");
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiClient(builder.build(), mapper, "test-key", "test-model");
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
    @Test void validStructuredDiyResponse() throws Exception {
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
        server.expect(anything()).andRespond(withSuccess("{malformed", MediaType.APPLICATION_JSON));
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap problem", "Other", categories));
    }
    @Test void missingKeyFailsWithoutNetworkCall() {
        var absent = new GeminiClient(RestClient.create(), mapper, "", "test-model");
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> absent.analyze("Tap", "Other", categories)); server.verify();
    }
    @Test void invalidKeyHandled() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
    }
    @Test void rateLimitHandled() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThrows(GeminiClient.AIServiceUnavailableException.class, () -> client.analyze("Tap", "Other", categories));
    }
    @Test void serverErrorHandled() {
        server.expect(anything()).andRespond(withServerError());
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
}
