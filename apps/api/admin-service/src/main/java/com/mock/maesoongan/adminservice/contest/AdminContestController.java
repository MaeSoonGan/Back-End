package com.mock.maesoongan.adminservice.contest;

import com.mock.maesoongan.adminservice.common.ApiResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestCreateRequest;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestDetailResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestListItem;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestMutationResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestResultResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestSummaryResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestUpdateRequest;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.PageResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.RankingExcludeRequest;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.RankingItem;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.RankingStatsResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.RankingStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Tag(name = "Admin Contests", description = "Admin contest and ranking management API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/contests")
public class AdminContestController {

    private final AdminContestService adminContestService;

    public AdminContestController(AdminContestService adminContestService) {
        this.adminContestService = adminContestService;
    }

    @Operation(summary = "Get contest summary cards")
    @GetMapping("/summary")
    public ApiResponse<ContestSummaryResponse> getContestSummary() {
        return ApiResponse.success(adminContestService.getContestSummary());
    }

    @Operation(summary = "Get paged contest list")
    @GetMapping
    public ApiResponse<PageResponse<ContestListItem>> getContests(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(adminContestService.getContests(keyword, status, startDate, endDate, page, size));
    }

    @Operation(summary = "Create contest")
    @PostMapping
    public ApiResponse<ContestMutationResponse> createContest(@Valid @RequestBody ContestCreateRequest request) {
        return ApiResponse.success(adminContestService.createContest(request));
    }

    @Operation(summary = "Get contest detail")
    @GetMapping("/{contestId}")
    public ApiResponse<ContestDetailResponse> getContest(@PathVariable long contestId) {
        return ApiResponse.success(adminContestService.getContest(contestId));
    }

    @Operation(summary = "Update contest")
    @PutMapping("/{contestId}")
    public ApiResponse<ContestMutationResponse> updateContest(
            @PathVariable long contestId,
            @Valid @RequestBody ContestUpdateRequest request
    ) {
        return ApiResponse.success(adminContestService.updateContest(contestId, request));
    }

    @Operation(summary = "End contest")
    @PatchMapping("/{contestId}/end")
    public ApiResponse<ContestMutationResponse> endContest(@PathVariable long contestId) {
        return ApiResponse.success(adminContestService.endContest(contestId));
    }

    @Operation(summary = "Cancel contest")
    @PatchMapping("/{contestId}/cancel")
    public ApiResponse<ContestMutationResponse> cancelContest(@PathVariable long contestId) {
        return ApiResponse.success(adminContestService.cancelContest(contestId));
    }

    @Operation(summary = "Get contest result")
    @GetMapping("/{contestId}/result")
    public ApiResponse<ContestResultResponse> getContestResult(@PathVariable long contestId) {
        return ApiResponse.success(adminContestService.getContestResult(contestId));
    }

    @Operation(summary = "Get contest ranking stats")
    @GetMapping("/{contestId}/rankings/stats")
    public ApiResponse<RankingStatsResponse> getRankingStats(@PathVariable long contestId) {
        return ApiResponse.success(adminContestService.getRankingStats(contestId));
    }

    @Operation(summary = "Get paged contest rankings")
    @GetMapping("/{contestId}/rankings")
    public ApiResponse<PageResponse<RankingItem>> getRankings(
            @PathVariable long contestId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String excluded,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(adminContestService.getRankings(contestId, keyword, excluded, page, size));
    }

    @Operation(summary = "Export contest rankings as CSV")
    @GetMapping("/{contestId}/rankings/export")
    public ResponseEntity<byte[]> exportRankings(
            @PathVariable long contestId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String excluded
    ) {
        return csv("contest-" + contestId + "-rankings.csv", adminContestService.exportRankings(contestId, keyword, excluded));
    }

    @Operation(summary = "Exclude member from contest ranking")
    @PatchMapping("/{contestId}/rankings/{memberId}/exclude")
    public ApiResponse<RankingStatusResponse> excludeRanking(
            @PathVariable long contestId,
            @PathVariable long memberId,
            @Valid @RequestBody RankingExcludeRequest request
    ) {
        return ApiResponse.success(adminContestService.excludeRanking(contestId, memberId, request));
    }

    @Operation(summary = "Restore member to contest ranking")
    @PatchMapping("/{contestId}/rankings/{memberId}/restore")
    public ApiResponse<RankingStatusResponse> restoreRanking(
            @PathVariable long contestId,
            @PathVariable long memberId
    ) {
        return ApiResponse.success(adminContestService.restoreRanking(contestId, memberId));
    }

    private ResponseEntity<byte[]> csv(String filename, String csv) {
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
