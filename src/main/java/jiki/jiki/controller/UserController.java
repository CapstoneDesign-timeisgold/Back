package jiki.jiki.controller;

import jakarta.validation.Valid;
import jiki.jiki.service.UserSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jiki.jiki.service.UserService;
import lombok.RequiredArgsConstructor;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSecurityService userSecurityService;

    @PostMapping("/user/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody Map<String, String> userCreateForm) {
        Map<String, Object> resultMap = userService.createUser(userCreateForm);
        return new ResponseEntity<>(resultMap, resultMap.containsKey("error") ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK);
    }

    @PostMapping("/user/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        Map<String, Object> resultMap = userSecurityService.authenticateUser(loginRequest);
        return new ResponseEntity<>(resultMap, resultMap.containsKey("error") ? HttpStatus.UNAUTHORIZED : HttpStatus.OK);
    }

    @GetMapping("/user/money")
    public ResponseEntity<Map<String, Object>> getUserMoney(@RequestHeader("username") String username) {
        Map<String, Object> resultMap = userService.getUserMoney(username);
        return new ResponseEntity<>(resultMap, resultMap.containsKey("error") ? HttpStatus.NOT_FOUND : HttpStatus.OK);
    }
}

