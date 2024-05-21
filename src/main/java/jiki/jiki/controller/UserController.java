package jiki.jiki.controller;

import jakarta.validation.Valid;
import  jiki.jiki.service.UserSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import  jiki.jiki.service.UserService;
import lombok.RequiredArgsConstructor;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSecurityService userSecurityService;

    @PostMapping("/user/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody Map<String, String> userCreateForm) {
        Map<String, Object> resultMap = new HashMap<>();

        try {
            userService.create(userCreateForm.get("username"),userCreateForm.get("nickname"), userCreateForm.get("email"), userCreateForm.get("password1"));
            resultMap.put("message", "User signed up successfully!");
            return ResponseEntity.ok().body(resultMap);
        } catch (Exception e) {
            resultMap.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }
    }
    @PostMapping("/user/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        Map<String, Object> resultMap = new HashMap<>();
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        try {
            userSecurityService.loadUserByUsername(username); // 사용자 정보를 가져옴 (실제로는 인증을 수행함)
            String nickname = userSecurityService.getUserNickname(username); // 사용자의 닉네임 정보를 가져옴
            resultMap.put("message", "Login successful!");
            resultMap.put("nickname", nickname); // 사용자의 닉네임 정보를 함께 응답에 포함
            return ResponseEntity.ok().body(resultMap);
        } catch (Exception e) {
            resultMap.put("error", "Login failed. Invalid username or password.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resultMap);
        }
    }

}
