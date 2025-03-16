package jiki.jiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jiki.jiki.dto.MoneyDto;
import jiki.jiki.dto.MoneyRecordDto;
import jiki.jiki.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
@Tag(name = "Payment API", description = "벌금 및 결제 관련 API")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/admin/money")
    @Operation(summary = "관리자 벌금 총액 조회", description = "관리자의 현재 보유 벌금 총액을 조회합니다.")
    public ResponseEntity<MoneyDto> getAdminMoney() {
        MoneyDto adminMoneyDto = paymentService.getAdminMoney();
        return ResponseEntity.ok(adminMoneyDto);
    }

    @GetMapping("/money-records")
    @Operation(summary = "사용자 거래 내역 조회", description = "사용자의 벌금 및 거래 내역을 조회합니다.")
    public ResponseEntity<List<MoneyRecordDto>> getUserMoneyRecords(
            @RequestHeader("username") String username) {
        List<MoneyRecordDto> records = paymentService.getUserMoneyRecords(username);
        return ResponseEntity.ok(records);
    }
}