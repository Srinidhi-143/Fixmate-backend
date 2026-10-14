package com.fixmate.service;
import com.fixmate.dto.AdminReportResponse;
import com.fixmate.entity.RequestStatus;
import com.fixmate.entity.UserRole;
import com.fixmate.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;

@Service
public class AdminReportService {
    private final UserRepository users;
    private final ServiceRequestService requests;
    private final ServiceProviderService providers;
    public AdminReportService(UserRepository users, ServiceRequestService requests, ServiceProviderService providers) {
        this.users = users; this.requests = requests; this.providers = providers;
    }
    @Transactional(readOnly = true)
    public AdminReportResponse report() {
        var accounts = users.findAll();
        var allRequests = requests.getAllRequests();
        var byStatus = new LinkedHashMap<String, Long>();
        for (var status : RequestStatus.values()) byStatus.put(status.name(), allRequests.stream().filter(r -> r.status() == status).count());
        return new AdminReportResponse(accounts.size(), accounts.stream().filter(u -> u.getRole() == UserRole.USER).count(),
                accounts.stream().filter(u -> u.getRole() == UserRole.PROVIDER).count(), allRequests.size(), byStatus,
                allRequests.stream().limit(10).toList(), providers.getProviders());
    }
}
