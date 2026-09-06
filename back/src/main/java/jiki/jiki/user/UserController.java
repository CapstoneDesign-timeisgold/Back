package jiki.jiki.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jiki.jiki.settlement.MoneyDto;
import jiki.jiki.settlement.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
@Tag(name = "User API", description = "회원 관련 API")
public class UserController {

    private final UserService userService;
    private final UserSecurityService userSecurityService;
    private final SettlementService settlementService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    public ResponseEntity<Map<String, String>> signup(@Valid @RequestBody UserSignupDto userCreateForm) {
        Map<String, String> result = userService.createUser(userCreateForm);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 로그인 API")
    public ResponseEntity<LoginResponseDto> login(@RequestBody Map<String, String> loginRequest) {
        LoginResponseDto result = userSecurityService.authenticateUser(loginRequest);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/money")
    @Operation(summary = "잔액 조회", description = "사용자의 현재 잔액을 조회합니다.")
    public ResponseEntity<MoneyDto> getUserMoney(Authentication authentication) {
        MoneyDto moneyDto = settlementService.getUserMoney(authentication.getName());
        return ResponseEntity.ok(moneyDto);
    }
}