package com.fixmate.service;

import com.fixmate.client.GeminiClient;
import com.fixmate.dto.AIAnalysisResponse;
import com.fixmate.dto.ServiceRequestRequest;
import com.fixmate.entity.*;
import com.fixmate.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.HttpStatus;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@ExtendWith(MockitoExtension.class)
class AIRequestFlowTest {
    @Mock ServiceRequestRepository requests;
    @Mock ServiceCategoryRepository categories;
    @Mock ServiceProviderRepository providers;
    @Mock UserRepository users;
    @Mock AIAnalysisRepository analyses;
    @Mock GeminiClient gemini;
    @Mock NotificationService notifications;
    @InjectMocks ServiceRequestService service;

    private void setup() {
        var customer = new User("Customer", "customer@example.test", "hash", UserRole.USER);
        var other = new ServiceCategory("Other", "other", null);
        var laptop = new ServiceCategory("Laptop Repair", "laptop-repair", null);
        ReflectionTestUtils.setField(other, "id", 1L);
        ReflectionTestUtils.setField(laptop, "id", 2L);
        when(users.findByEmailForUpdate(customer.getEmail())).thenReturn(Optional.of(customer));
        when(categories.findById(1L)).thenReturn(Optional.of(other));
        when(categories.findAllByOrderByNameAsc()).thenReturn(List.of(laptop, other));
        when(requests.save(any())).thenAnswer(invocation -> {
            ServiceRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", 10L); return request;
        });
    }

    private ServiceRequestRequest payload() {
        return new ServiceRequestRequest(1L, "Laptop problem", "Laptop will not start", "Test address",
                "9999999999", 12.97, 77.59, null, null, RequestPriority.NORMAL);
    }

    @Test void unavailableGeminiPreservesSavedRequestAndSelectedCategory() {
        setup();
        when(gemini.analyze(anyString(), anyString(), anyList()))
                .thenThrow(new GeminiClient.AIServiceUnavailableException("Gemini is temporarily unavailable"));
        var result = service.createRequest("customer@example.test", payload(), null);
        assertEquals(10L, result.requestId()); assertEquals(RequestStatus.PENDING, result.status());
        assertEquals("Other", result.categoryName()); assertNull(result.aiAnalysis());
        verify(requests).save(any()); verifyNoInteractions(analyses);
        verify(notifications).notifyRequestOwner(any(), eq(NotificationType.NEW_SERVICE_REQUEST), anyString(), anyString());
    }

    @Test void technicianDecisionChangesRequestCategoryAndPersistsDecision() {
        setup();
        when(gemini.analyze(anyString(), anyString(), anyList())).thenReturn(new AIAnalysisResponse(
                "Laptop Repair", "Laptop fault", List.of("Stop using the laptop"), "Laptop Repair",
                "Seek professional assessment", "HIGH", 0.8, "NOT_PROVIDED", false, true, List.of("Battery fault")));
        when(analyses.save(any())).thenAnswer(invocation -> {
            AIAnalysis analysis = invocation.getArgument(0);
            ReflectionTestUtils.setField(analysis, "id", 20L); return analysis;
        });
        var result = service.createRequest("customer@example.test", payload(), null);
        assertEquals(2L, result.categoryId()); assertEquals("Laptop Repair", result.categoryName());
        assertEquals(RequestStatus.AI_ANALYZED, result.status()); assertEquals(20L, result.aiAnalysisId());
        var saved = ArgumentCaptor.forClass(AIAnalysis.class); verify(analyses).save(saved.capture());
        assertTrue(saved.getValue().getTechnicianRequired()); assertFalse(saved.getValue().getCanSelfFix());
        assertEquals("GEMINI", saved.getValue().getSource());
    }

    @Test void bothActualClientAttemptsFailButServiceStillSavesRequestWithoutAi() {
        setup();
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta");
        var server = MockRestServiceServer.bindTo(builder).build();
        var actualClient = new GeminiClient(builder.build(), new ObjectMapper(), "test-key", "gemini-3.8-flash", "gemini-3.6-flash");
        ReflectionTestUtils.setField(service, "aiServiceClient", actualClient);
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var result = service.createRequest("customer@example.test", payload(), null);
        assertEquals(10L, result.requestId()); assertEquals(RequestStatus.PENDING, result.status());
        assertNull(result.aiAnalysis()); assertNull(result.aiAnalysisId());
        verify(requests).save(any()); verifyNoInteractions(analyses); server.verify();
    }
}
