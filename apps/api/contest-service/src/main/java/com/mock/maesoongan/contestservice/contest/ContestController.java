package com.mock.maesoongan.contestservice.contest;

import com.mock.maesoongan.contestservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.contestservice.common.ApiResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestDetailResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestJoinResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestListItem;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestResultResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestStockItem;
import com.mock.maesoongan.contestservice.contest.ContestDtos.MyContestListItem;
import com.mock.maesoongan.contestservice.contest.ContestDtos.MyRankingResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.OrderValidationRequest;
import com.mock.maesoongan.contestservice.contest.ContestDtos.OrderValidationResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.PageResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.RankingItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Contests", description = "Contest and ranking API")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class ContestController {

    private final ContestService contestService;
    private final CurrentMemberProvider currentMemberProvider;

    public ContestController(ContestService contestService, CurrentMemberProvider currentMemberProvider) {
        this.contestService = contestService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @Operation(summary = "Get contest list")
    @GetMapping("/api/contests")
    public ApiResponse<PageResponse<ContestListItem>> getContests(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "ALL") String participation,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(contestService.getContests(
                currentMemberProvider.memberId(),
                keyword,
                status,
                participation,
                page,
                size
        ));
    }

    @Operation(summary = "Get my contest list")
    @GetMapping("/api/contests/my")
    public ApiResponse<PageResponse<MyContestListItem>> getMyContests(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(contestService.getMyContests(currentMemberProvider.memberId(), status, page, size));
    }

    @Operation(summary = "Get contest detail")
    @GetMapping("/api/contests/{contestId}")
    public ApiResponse<ContestDetailResponse> getContest(@PathVariable long contestId) {
        return ApiResponse.success(contestService.getContest(contestId, currentMemberProvider.memberId()));
    }

    @Operation(summary = "Join contest")
    @PostMapping("/api/contests/{contestId}/join")
    public ApiResponse<ContestJoinResponse> joinContest(@PathVariable long contestId) {
        return ApiResponse.success(contestService.joinContest(contestId, currentMemberProvider.memberId()));
    }

    @Operation(summary = "Get tradable stocks in contest")
    @GetMapping("/api/contests/{contestId}/stocks")
    public ApiResponse<PageResponse<ContestStockItem>> getContestStocks(
            @PathVariable long contestId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String market,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(contestService.getContestStocks(contestId, keyword, market, page, size));
    }

    @Operation(summary = "Get contest rankings")
    @GetMapping("/api/contests/{contestId}/rankings")
    public ApiResponse<PageResponse<RankingItem>> getRankings(
            @PathVariable long contestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(contestService.getRankings(contestId, page, size));
    }

    @Operation(summary = "Get my contest ranking")
    @GetMapping("/api/contests/{contestId}/rankings/me")
    public ApiResponse<MyRankingResponse> getMyRanking(@PathVariable long contestId) {
        return ApiResponse.success(contestService.getMyRanking(contestId, currentMemberProvider.memberId()));
    }

    @Operation(summary = "Get ended contest result")
    @GetMapping("/api/contests/{contestId}/result")
    public ApiResponse<ContestResultResponse> getContestResult(
            @PathVariable long contestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(contestService.getContestResult(contestId, currentMemberProvider.memberId(), page, size));
    }

    @Operation(summary = "Validate contest order context for internal services")
    @PostMapping("/internal/contests/{contestId}/order-validation")
    public ApiResponse<OrderValidationResponse> validateOrder(
            @PathVariable long contestId,
            @Valid @RequestBody OrderValidationRequest request
    ) {
        return ApiResponse.success(contestService.validateOrder(contestId, request));
    }
}
