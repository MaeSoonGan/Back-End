package com.mock.maesoongan.notificationapi.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notification_setting",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_setting_member", columnNames = "member_id")
)
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "trade_complete", nullable = false)
    private boolean tradeComplete = true;

    @Column(name = "order_cancel", nullable = false)
    private boolean orderCancel = true;

    @Column(name = "pending_order", nullable = false)
    private boolean pendingOrder;

    @Column(name = "contest_start", nullable = false)
    private boolean contestStart = true;

    @Column(name = "contest_end", nullable = false)
    private boolean contestEnd = true;

    @Column(name = "rank_change", nullable = false)
    private boolean rankChange;

    @Column(name = "market_open", nullable = false)
    private boolean marketOpen;

    @Column(name = "market_close", nullable = false)
    private boolean marketClose;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NotificationSetting() {
    }

    private NotificationSetting(Long memberId) {
        this.memberId = memberId;
    }

    public static NotificationSetting defaultFor(Long memberId) {
        return new NotificationSetting(memberId);
    }

    @PrePersist
    void prePersist() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public boolean isTradeComplete() {
        return tradeComplete;
    }

    public boolean isOrderCancel() {
        return orderCancel;
    }

    public boolean isPendingOrder() {
        return pendingOrder;
    }

    public boolean isContestStart() {
        return contestStart;
    }

    public boolean isContestEnd() {
        return contestEnd;
    }

    public boolean isRankChange() {
        return rankChange;
    }

    public boolean isMarketOpen() {
        return marketOpen;
    }

    public boolean isMarketClose() {
        return marketClose;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateTradeSettings(Boolean tradeComplete, Boolean orderCancel, Boolean pendingOrder) {
        if (tradeComplete != null) {
            this.tradeComplete = tradeComplete;
        }
        if (orderCancel != null) {
            this.orderCancel = orderCancel;
        }
        if (pendingOrder != null) {
            this.pendingOrder = pendingOrder;
        }
    }

    public void updateContestSettings(Boolean contestStart, Boolean contestEnd, Boolean rankChange) {
        if (contestStart != null) {
            this.contestStart = contestStart;
        }
        if (contestEnd != null) {
            this.contestEnd = contestEnd;
        }
        if (rankChange != null) {
            this.rankChange = rankChange;
        }
    }

    public void updateMarketSettings(Boolean marketOpen, Boolean marketClose) {
        if (marketOpen != null) {
            this.marketOpen = marketOpen;
        }
        if (marketClose != null) {
            this.marketClose = marketClose;
        }
    }
}
