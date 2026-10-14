package com.fixmate.controller;
import com.fixmate.dto.AdminReportResponse;
import com.fixmate.service.AdminReportService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin")
public class AdminReportController {
    private final AdminReportService service;
    public AdminReportController(AdminReportService service) { this.service = service; }
    @GetMapping({"/dashboard", "/reports"})
    public AdminReportResponse report() { return service.report(); }
}
