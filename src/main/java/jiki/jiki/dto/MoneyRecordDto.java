package jiki.jiki.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MoneyRecordDto {
    private Long id;
    private String promiseTitle;
    private int amount;
    private Boolean isPenalty;
    private LocalDateTime transactionDate;
    private int balanceAfterTransaction;
}