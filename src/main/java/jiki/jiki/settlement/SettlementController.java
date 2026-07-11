package jiki.jiki.settlement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settlement")
@Tag(name = "Settlement API", description = "벌금 및 정산 관련 API")
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/admin/money")
    @Operation(summary = "관리자 벌금 총액 조회", description = "관리자의 현재 보유 벌금 총액을 조회합니다.")
    public ResponseEntity<MoneyDto> getAdminMoney() {
        MoneyDto adminMoneyDto = settlementService.getAdminMoney();
        return ResponseEntity.ok(adminMoneyDto);
    }

    @GetMapping("/money-records")
    @Operation(summary = "사용자 거래 내역 조회", description = "사용자의 벌금 및 거래 내역을 조회합니다.")
    public ResponseEntity<List<MoneyRecordDto>> getUserMoneyRecords(
            @RequestHeader("username") String username) {
        List<MoneyRecordDto> records = settlementService.getUserMoneyRecords(username);
        return ResponseEntity.ok(records);
    }

    @GetMapping("/{promiseId}/result")
    @Operation(summary = "벌금 정산 결과 조회", description = "약속의 벌금 정산 결과를 조회합니다.")
    public ResponseEntity<PromiseResultDto> getPromiseResultDetails(@PathVariable("promiseId") Long promiseId,
                                                                     @RequestHeader("username") String guestUsername) {
        PromiseResultDto settlementDetails = settlementService.getPromiseResultDetails(promiseId);
        return ResponseEntity.ok(settlementDetails);
    }

    @PostMapping("/reward")
    @Operation(summary = "보상 지급 결정", description = "벌금 및 보상을 정산합니다.")
    public ResponseEntity<Void> decideRewards(@RequestBody RewardDto rewardDto) {
        settlementService.decideRewards(rewardDto);
        return ResponseEntity.noContent().build();
    }
}
