package com.mock.maesoongan.admin.member;

import com.mock.maesoongan.admin.member.AdminMemberDtos.AddMemberRequest;
import com.mock.maesoongan.admin.member.AdminMemberDtos.AddMemberResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.BatchSeedMoneyResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.BatchSuspendResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberDetailResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberListItem;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberPageResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberSummaryResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.SeedMoneyResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.SuspendMemberResponse;
import com.mock.maesoongan.admin.member.entity.AccountSuspension;
import com.mock.maesoongan.admin.member.entity.AuditLog;
import com.mock.maesoongan.admin.member.entity.SeedHistory;
import com.mock.maesoongan.admin.member.repository.AccountSuspensionRepository;
import com.mock.maesoongan.admin.member.repository.AuditLogRepository;
import com.mock.maesoongan.admin.member.repository.ContestParticipationRepository;
import com.mock.maesoongan.admin.member.repository.SeedHistoryRepository;
import com.mock.maesoongan.common.BusinessException;
import com.mock.maesoongan.member.entity.Member;
import com.mock.maesoongan.member.entity.MemberStatus;
import com.mock.maesoongan.member.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AdminMemberService {

    private static final long ADMIN_ID = 1L;
    private static final DateTimeFormatter LIST_DATE_FORMATTER = DateTimeFormatter.ofPattern("yy.MM.dd");
    private static final DateTimeFormatter DETAIL_DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    private static final List<String> STATUSES = List.of("ALL", "ACTIVE", "SUSPENDED", "TODAY");

    private final MemberRepository memberRepository;
    private final ContestParticipationRepository participationRepository;
    private final AccountSuspensionRepository suspensionRepository;
    private final SeedHistoryRepository seedHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminMemberService(MemberRepository memberRepository,
                              ContestParticipationRepository participationRepository,
                              AccountSuspensionRepository suspensionRepository,
                              SeedHistoryRepository seedHistoryRepository,
                              AuditLogRepository auditLogRepository,
                              PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.participationRepository = participationRepository;
        this.suspensionRepository = suspensionRepository;
        this.seedHistoryRepository = seedHistoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public MemberSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();
        return new MemberSummaryResponse(
                toInt(memberRepository.count()),
                toInt(memberRepository.countByStatus(MemberStatus.ACTIVE)),
                toInt(memberRepository.countByStatus(MemberStatus.SUSPENDED)),
                toInt(memberRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay()
                ))
        );
    }

    public MemberPageResponse getMembers(String keyword, String status, LocalDate startDate, LocalDate endDate,
                                         int page, int size) {
        validatePage(page, size);
        List<Member> filtered = filterMembers(keyword, status, startDate, endDate);

        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<MemberListItem> content = filtered.subList(fromIndex, toIndex).stream()
                .map(this::toListItem)
                .toList();

        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);
        return new MemberPageResponse(content, filtered.size(), totalPages, page);
    }

    public MemberDetailResponse getMember(long userId) {
        return toDetail(findMember(userId));
    }

    @Transactional
    public AddMemberResponse addMember(AddMemberRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DUPLICATE_EMAIL", "이미 가입된 이메일입니다.");
        }

        long nextId = memberRepository.findMaxId() + 1;
        Member member = Member.create(
                "user" + String.format("%03d", nextId),
                passwordEncoder.encode(request.password()),
                request.email(),
                request.nickname(),
                null
        );
        Member saved = memberRepository.save(member);
        saveAuditLog("CREATE_MEMBER", "MEMBER", saved.getId(), "관리자 회원 직접 추가");

        return new AddMemberResponse(saved.getId(), "회원이 추가되었습니다.");
    }

    public byte[] exportCsv(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        StringBuilder csv = new StringBuilder();
        csv.append("nickname,accountId,email,joinDate,contestCount,totalAsset,profitRate,loginFailCount,status\n");

        for (Member member : filterMembers(keyword, status, startDate, endDate)) {
            Double profitRate = profitRateOf(member);
            csv.append(escape(member.getNickname())).append(',')
                    .append(escape(member.getLoginId())).append(',')
                    .append(escape(member.getEmail())).append(',')
                    .append(member.getCreatedAt().toLocalDate()).append(',')
                    .append(participationRepository.countByMemberId(member.getId())).append(',')
                    .append(member.getTotalAsset() == null ? "" : member.getTotalAsset().longValue()).append(',')
                    .append(profitRate == null ? "" : profitRate).append(',')
                    .append(loginFailCountOf(member)).append(',')
                    .append(member.getStatus().name()).append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public SuspendMemberResponse suspendMember(long userId) {
        Member member = findMember(userId);
        member.suspend();
        saveSuspension(userId, "관리자 회원 관리 화면 계정 정지");
        saveAuditLog("SUSPEND_MEMBER", "MEMBER", userId, "관리자 회원 관리 화면 계정 정지");
        return new SuspendMemberResponse(userId, "SUSPENDED", "계정이 정지되었습니다.");
    }

    @Transactional
    public BatchSuspendResponse suspendMembers(List<Long> userIds) {
        validateIds(userIds);

        int count = 0;
        for (Long userId : userIds) {
            Member member = memberRepository.findById(userId).orElse(null);
            if (member != null) {
                member.suspend();
                saveSuspension(userId, "관리자 회원 관리 화면 일괄 계정 정지");
                saveAuditLog("SUSPEND_MEMBER", "MEMBER", userId, "관리자 회원 관리 화면 일괄 계정 정지");
                count++;
            }
        }

        return new BatchSuspendResponse(count, count + "명의 계정이 정지되었습니다.");
    }

    @Transactional
    public SeedMoneyResponse paySeedMoney(long userId, long seedAmount) {
        validateSeedAmount(seedAmount);
        Member member = findMember(userId);
        BigDecimal amount = BigDecimal.valueOf(seedAmount);
        member.paySeedMoney(amount);
        saveSeedHistory(userId, amount, "관리자 시드머니 지급");
        saveAuditLog("SEED_MONEY_PAYMENT", "MEMBER", userId, "관리자 시드머니 지급");
        return new SeedMoneyResponse(userId, seedAmount, "시드머니가 지급되었습니다.");
    }

    @Transactional
    public BatchSeedMoneyResponse paySeedMoneyToMembers(List<Long> userIds, long seedAmount) {
        validateIds(userIds);
        validateSeedAmount(seedAmount);
        BigDecimal amount = BigDecimal.valueOf(seedAmount);

        int count = 0;
        for (Long userId : userIds) {
            Member member = memberRepository.findById(userId).orElse(null);
            if (member != null) {
                member.paySeedMoney(amount);
                saveSeedHistory(userId, amount, "관리자 일괄 시드머니 지급");
                saveAuditLog("SEED_MONEY_PAYMENT", "MEMBER", userId, "관리자 일괄 시드머니 지급");
                count++;
            }
        }

        return new BatchSeedMoneyResponse(count, count + "명에게 시드머니가 지급되었습니다.");
    }

    private List<Member> filterMembers(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        validateStatus(status);
        validateDateRange(startDate, endDate);

        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        return memberRepository.findAll().stream()
                .filter(member -> matchesKeyword(member, normalizedKeyword))
                .filter(member -> matchesStatus(member, status))
                .filter(member -> matchesDateRange(member, startDate, endDate))
                .sorted(Comparator.comparing(Member::getId))
                .toList();
    }

    private boolean matchesKeyword(Member member, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return member.getNickname().toLowerCase(Locale.ROOT).contains(keyword)
                || member.getEmail().toLowerCase(Locale.ROOT).contains(keyword)
                || member.getLoginId().toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean matchesStatus(Member member, String status) {
        if (status == null || status.isBlank() || "ALL".equals(status)) {
            return true;
        }
        if ("TODAY".equals(status)) {
            return member.getCreatedAt().toLocalDate().equals(LocalDate.now());
        }
        return member.getStatus().name().equals(status);
    }

    private boolean matchesDateRange(Member member, LocalDate startDate, LocalDate endDate) {
        LocalDate joinDate = member.getCreatedAt().toLocalDate();
        if (startDate != null && joinDate.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !joinDate.isAfter(endDate);
    }

    private MemberListItem toListItem(Member member) {
        return new MemberListItem(
                member.getId(),
                member.getNickname(),
                member.getLoginId(),
                maskEmail(member.getEmail()),
                LIST_DATE_FORMATTER.format(member.getCreatedAt()),
                participationRepository.countByMemberId(member.getId()),
                formatAsset(member.getTotalAsset()),
                profitRateOf(member),
                loginFailCountOf(member),
                member.getStatus().name()
        );
    }

    private MemberDetailResponse toDetail(Member member) {
        return new MemberDetailResponse(
                member.getId(),
                member.getNickname(),
                member.getLoginId(),
                member.getEmail(),
                DETAIL_DATE_FORMATTER.format(member.getCreatedAt()),
                participationRepository.countByMemberId(member.getId()),
                member.getTotalAsset() == null ? null : member.getTotalAsset().longValue(),
                profitRateOf(member),
                loginFailCountOf(member),
                member.getStatus().name()
        );
    }

    private Member findMember(long userId) {
        return memberRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));
    }

    private void saveSuspension(long userId, String reason) {
        suspensionRepository.save(new AccountSuspension(
                suspensionRepository.findMaxId() + 1,
                userId,
                ADMIN_ID,
                reason,
                "SUSPENDED",
                LocalDateTime.now()
        ));
    }

    private void saveSeedHistory(long userId, BigDecimal amount, String reason) {
        seedHistoryRepository.save(new SeedHistory(
                seedHistoryRepository.findMaxId() + 1,
                userId,
                ADMIN_ID,
                amount,
                reason,
                LocalDateTime.now()
        ));
    }

    private void saveAuditLog(String action, String targetType, long targetId, String reason) {
        auditLogRepository.save(new AuditLog(
                auditLogRepository.findMaxId() + 1,
                ADMIN_ID,
                action,
                targetType,
                targetId,
                reason,
                LocalDateTime.now()
        ));
    }

    private Double profitRateOf(Member member) {
        Double average = participationRepository.findAverageProfitRateByMemberId(member.getId());
        if (average == null) {
            return null;
        }
        return Math.round(average * 10) / 10.0;
    }

    private int loginFailCountOf(Member member) {
        return member.getLoginFailCount() == null ? 0 : member.getLoginFailCount();
    }

    private void validateStatus(String status) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "잘못된 회원 상태입니다.");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "가입일 조회 범위가 올바르지 않습니다.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "페이지 요청 값이 올바르지 않습니다.");
        }
    }

    private void validateIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_USER_IDS", "회원 ID 목록은 필수입니다.");
        }
    }

    private void validateSeedAmount(long seedAmount) {
        if (seedAmount <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SEED_AMOUNT", "시드머니 금액이 올바르지 않습니다.");
        }
    }

    private int toInt(long value) {
        return Math.toIntExact(value);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, Math.min(4, atIndex)) + "***" + email.substring(atIndex);
    }

    private String formatAsset(BigDecimal totalAsset) {
        if (totalAsset == null) {
            return null;
        }
        if (totalAsset.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            double value = Math.round((totalAsset.doubleValue() / 1_000_000.0) * 10) / 10.0;
            return value + "M원";
        }
        return totalAsset.longValue() + "원";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
