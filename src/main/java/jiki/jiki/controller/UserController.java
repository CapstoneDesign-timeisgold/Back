package jiki.jiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jiki.jiki.dto.UserCreateForm;
import jiki.jiki.service.UserSecurityService;
import jiki.jiki.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
@Tag(name = "User API", description = "회원 관련 API")
public class UserController {

    private final UserService userService;
    private final UserSecurityService userSecurityService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody UserCreateForm userCreateForm) {
        Map<String, Object> resultMap = userService.createUser(userCreateForm);
        return new ResponseEntity<>(resultMap, resultMap.containsKey("error") ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK);
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 로그인 API")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        Map<String, Object> resultMap = userSecurityService.authenticateUser(loginRequest);
        return new ResponseEntity<>(resultMap, resultMap.containsKey("error") ? HttpStatus.UNAUTHORIZED : HttpStatus.OK);
    }

    @GetMapping("/money")
    @Operation(summary = "잔액 조회", description = "사용자의 현재 잔액을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getUserMoney(@RequestHeader("username") String username) {
        Map<String, Object> resultMap = userService.getUserMoney(username);
        return new ResponseEntity<>(resultMap, resultMap.containsKey("error") ? HttpStatus.NOT_FOUND : HttpStatus.OK);
    }
}