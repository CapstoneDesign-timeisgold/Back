package jiki.jiki.controller;

import jiki.jiki.dto.MoneyDto;
import jiki.jiki.dto.MoneyRecordDto;
import jiki.jiki.dto.RewardDto;
import jiki.jiki.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // 모든 벌금액 조회
    @GetMapping("/admin/money")
    public ResponseEntity<MoneyDto> getAdminMoney() {
        MoneyDto adminMoneyDto = paymentService.getAdminMoney();
        return ResponseEntity.ok(adminMoneyDto);
    }

    // 사용자 거래 내역 조회
    @GetMapping("/money-records")
    public ResponseEntity<List<MoneyRecordDto>> getUserMoneyRecords(@RequestHeader("username") String username) {
        List<MoneyRecordDto> records = paymentService.getUserMoneyRecords(username);
        return ResponseEntity.ok(records);
    }
}