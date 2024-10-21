package jiki.jiki.controller;

import jiki.jiki.domain.SiteUser;
import jiki.jiki.dto.MoneyDto;
import jiki.jiki.dto.RewardDto;
import jiki.jiki.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payment/reward")
    public ResponseEntity<Void> decideRewards(@RequestBody RewardDto rewardDto) {
        paymentService.decideRewards(rewardDto);
        return ResponseEntity.ok().build();
    }

    // 모든 벌금액
    @GetMapping("/admin/money")
    public ResponseEntity<MoneyDto> getAdminMoney() {
        MoneyDto adminMoneyDto = paymentService.getAdminMoney();
        return ResponseEntity.ok(adminMoneyDto);
    }
}