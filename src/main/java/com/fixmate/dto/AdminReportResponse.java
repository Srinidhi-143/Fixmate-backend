package com.fixmate.dto;
import java.util.List;
import java.util.Map;
public record AdminReportResponse(long totalUsers, long totalCustomers, long totalProviders, long totalRequests,
                                  Map<String, Long> requestsByStatus, List<ServiceRequestResponse> recentRequests,
                                  List<ProviderProfileResponse> registeredProviders) {}
