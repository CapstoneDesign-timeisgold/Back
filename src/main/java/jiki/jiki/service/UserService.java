package jiki.jiki.service;

import jiki.jiki.domain.SiteUser;
import jiki.jiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Map<String, Object> createUser(Map<String, String> userCreateForm) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            SiteUser user = new SiteUser();
            user.setUsername(userCreateForm.get("username"));
            user.setNickname(userCreateForm.get("nickname"));
            user.setEmail(userCreateForm.get("email"));
            user.setPassword(passwordEncoder.encode(userCreateForm.get("password1")));
            this.userRepository.save(user);
            resultMap.put("message", "User signed up successfully!");
        } catch (Exception e) {
            resultMap.put("error", e.getMessage());
        }
        return resultMap;
    }
}







