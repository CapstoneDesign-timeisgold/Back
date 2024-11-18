package jiki.jiki.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
public class MoneyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String promiseTitle;
    private int amount;                   // 벌금 or 보상
    private boolean isPenalty;
    private LocalDateTime transactionDate;

    private int balanceAfterTransaction;  // 거래 후 잔액

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private SiteUser user;
}