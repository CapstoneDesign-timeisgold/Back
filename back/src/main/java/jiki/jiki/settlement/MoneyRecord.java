package jiki.jiki.settlement;

import jakarta.persistence.*;
import jiki.jiki.user.SiteUser;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_record_promise_user_direction",
        columnNames = {"promise_id", "user_id", "is_penalty"}))
public class MoneyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String promiseTitle;
    // Nullable for historical rows; legacy rows cannot be safely matched by title alone.
    private Long promiseId;
    private int amount;                   // 벌금 or 보상
    private boolean isPenalty;
    private LocalDateTime transactionDate;

    private int balanceAfterTransaction;  // 거래 후 잔액

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private SiteUser user;
}
