package jiki.jiki.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class UserSecurityService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SiteUser siteUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
        return new User(siteUser.getUsername(), siteUser.getPassword(), new ArrayList<>());
    }

    public Map<String, String> authenticateUser(Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        SiteUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalStateException("Invalid username or password");
        }

        return Map.of("message", "Login successful!", "nickname", user.getNickname());
    }

    public String getUserNickname(String username) {
        return userRepository.findByUsername(username)
                .map(SiteUser::getNickname)
                .orElse(null);
    }
}





