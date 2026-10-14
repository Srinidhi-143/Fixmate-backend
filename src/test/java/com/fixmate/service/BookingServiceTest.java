package com.fixmate.service;
import com.fixmate.client.GeminiClient;
import com.fixmate.entity.*;
import com.fixmate.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock ServiceRequestRepository requests;
    @Mock ServiceCategoryRepository categories;
    @Mock ServiceProviderRepository providers;
    @Mock UserRepository users;
    @Mock AIAnalysisRepository analyses;
    @Mock GeminiClient gemini;
    @Mock NotificationService notifications;
    @InjectMocks ServiceRequestService service;
    ServiceRequest request;
    ServiceProvider provider;
    @BeforeEach void setup() {
        var customer = new User("Customer", "customer@example.test", "hash", UserRole.USER);
        var technician = new User("Technician", "provider@example.test", "hash", UserRole.PROVIDER);
        var category = new ServiceCategory("Plumbing", "plumbing", "Pipes");
        ReflectionTestUtils.setField(category, "id", 1L);
        request = new ServiceRequest(customer, category, "Pipe leak", "Leaking pipe", "Test address", 12.97, 77.59, null, null, RequestPriority.NORMAL);
        ReflectionTestUtils.setField(request, "id", 10L);
        provider = new ServiceProvider(technician, Set.of("pipes"), Set.of(category), 3, "Test address", 12.97, 77.59, 50.0, ProviderAvailability.AVAILABLE, "Repairs");
        ReflectionTestUtils.setField(provider, "id", 2L);
        when(requests.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
    }
    @Test void ownerBooksSpecificProviderWithoutAutoAccepting() {
        when(providers.findWithProfileById(2L)).thenReturn(Optional.of(provider));
        var result = service.bookProvider(10L, "customer@example.test", 2L);
        assertEquals(RequestStatus.REQUESTED, result.status()); assertEquals(2L, result.assignedProviderId());
    }
    @Test void otherCustomerCannotBook() {
        assertThrows(AccessDeniedException.class, () -> service.bookProvider(10L, "other@example.test", 2L));
    }
    @Test void disabledProviderCannotBeBooked() {
        provider.getUser().setActive(false);
        when(providers.findWithProfileById(2L)).thenReturn(Optional.of(provider));
        assertThrows(IllegalArgumentException.class, () -> service.bookProvider(10L, "customer@example.test", 2L));
    }
    @Test void providerCannotAcceptAnUnbookedRequest() {
        when(users.findByEmail("provider@example.test")).thenReturn(Optional.of(provider.getUser()));
        when(providers.findByUser_Id(null)).thenReturn(Optional.of(provider));
        assertThrows(AccessDeniedException.class, () -> service.updateStatus(10L, "provider@example.test", UserRole.PROVIDER, RequestStatus.ACCEPTED));
    }
}
