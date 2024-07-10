package jiki.jiki.controller;

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
}