package jiki.jiki.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Map<String, String> createUser(UserSignupDto userCreateForm) {
        if (userRepository.findByUsername(userCreateForm.getUsername()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        SiteUser user = new SiteUser();
        user.setUsername(userCreateForm.getUsername());
        user.setNickname(userCreateForm.getNickname());
        user.setEmail(userCreateForm.getEmail());
        user.setPassword(passwordEncoder.encode(userCreateForm.getPassword1()));
        userRepository.save(user);
        return Map.of("message", "User signed up successfully!");
    }
}







