package jiki.jiki.service;

import jiki.jiki.domain.SiteUser;
import jiki.jiki.dto.MoneyDto;
import jiki.jiki.dto.UserCreateForm;
import jiki.jiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Map<String, Object> createUser(UserCreateForm userCreateForm) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            SiteUser user = new SiteUser();
            user.setUsername(userCreateForm.getUsername());
            user.setNickname(userCreateForm.getNickname());
            user.setEmail(userCreateForm.getEmail());
            user.setPassword(passwordEncoder.encode(userCreateForm.getPassword1()));
            this.userRepository.save(user);
            resultMap.put("message", "User signed up successfully!");
        } catch (Exception e) {
            resultMap.put("error", e.getMessage());
        }
        return resultMap;
    }

    //개인 포인트 조회
    public Map<String, Object> getUserMoney(String username) {
        Map<String, Object> resultMap = new HashMap<>();
        Optional<SiteUser> userOptional = userRepository.findByUsername(username);
        if (userOptional.isPresent()) {
            SiteUser user = userOptional.get();
            MoneyDto moneyDto = MoneyDto.builder()
                    .money(user.getMoney())
                    .build();
            resultMap.put("money", moneyDto.getMoney());
        } else {
            resultMap.put("error", "User not found");
        }
        return resultMap;
    }
}







