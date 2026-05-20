package com.mock.maesoongan.admin.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "contest_participation")
public class ContestParticipation {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long contestId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal seedMoney;

    @Column(precision = 10, scale = 4)
    private BigDecimal profitRate;

    @Column(name = "`rank`")
    private Integer rank;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime updatedAt;

    protected ContestParticipation() {
    }

    public ContestParticipation(Long id, Long contestId, Long memberId, BigDecimal seedMoney,
                                BigDecimal profitRate, Integer rank, LocalDateTime joinedAt) {
        this.id = id;
        this.contestId = contestId;
        this.memberId = memberId;
        this.seedMoney = seedMoney;
        this.profitRate = profitRate;
        this.rank = rank;
        this.joinedAt = joinedAt;
    }

    public BigDecimal getProfitRate() {
        return profitRate;
    }
}
