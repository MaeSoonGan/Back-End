package com.mock.maesoongan.adminservice.system;

import com.mock.maesoongan.adminservice.common.ApiResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.ActiveUsersResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.AdminListItem;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.AlertMutationResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.AuditLogItem;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.AuditLogSummaryResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.IgnoreAlertRequest;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.MaintenanceResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.MaintenanceUpdateRequest;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.MonitoringAlertItem;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.MonitoringResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Admin System", description = "Admin system monitoring and audit log API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
public class AdminSystemController {

    private final AdminSystemService adminSystemService;

    public AdminSystemController(AdminSystemService adminSystemService) {
        this.adminSystemService = adminSystemService;
    }

    @Operation(summary = "Get monitoring screen data")
    @GetMapping("/monitoring")
    public ApiResponse<MonitoringResponse> getMonitoring() {
        return ApiResponse.success(adminSystemService.getMonitoring());
    }

    @Operation(summary = "Get active user count")
    @GetMapping("/monitoring/active-users")
    public ApiResponse<ActiveUsersResponse> getActiveUsers() {
        return ApiResponse.success(adminSystemService.getActiveUsers());
    }

    @Operation(summary = "Get monitoring alerts")
    @GetMapping("/monitoring/alerts")
    public ApiResponse<PageResponse<MonitoringAlertItem>> getAlerts(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(adminSystemService.getAlerts(status, page, size));
    }

    @Operation(summary = "Ignore monitoring alert")
    @PatchMapping("/monitoring/alerts/{alertId}/ignore")
    public ApiResponse<AlertMutationResponse> ignoreAlert(
            @PathVariable long alertId,
            @RequestBody(required = false) IgnoreAlertRequest request
    ) {
        return ApiResponse.success(adminSystemService.ignoreAlert(alertId, request));
    }

    @Operation(summary = "Get maintenance mode")
    @GetMapping("/system/maintenance")
    public ApiResponse<MaintenanceResponse> getMaintenance() {
        return ApiResponse.success(adminSystemService.getMaintenance());
    }

    @Operation(summary = "Update maintenance mode")
    @PatchMapping("/system/maintenance")
    public ApiResponse<MaintenanceResponse> updateMaintenance(@RequestBody MaintenanceUpdateRequest request) {
        return ApiResponse.success(adminSystemService.updateMaintenance(request));
    }

    @Operation(summary = "Get audit log summary")
    @GetMapping("/audit-logs/summary")
    public ApiResponse<AuditLogSummaryResponse> getAuditLogSummary() {
        return ApiResponse.success(adminSystemService.getAuditLogSummary());
    }

    @Operation(summary = "Get paged audit logs")
    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AuditLogItem>> getAuditLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) Long adminId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size
    ) {
        return ApiResponse.success(adminSystemService.getAuditLogs(keyword, startDate, endDate, type, adminId, page, size));
    }

    @Operation(summary = "Get admin list")
    @GetMapping("/admins")
    public ApiResponse<List<AdminListItem>> getAdmins() {
        return ApiResponse.success(adminSystemService.getAdmins());
    }
}
