package jiki.jiki.service;

import jiki.jiki.domain.SiteUser;
import jiki.jiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class UserSecurityService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<SiteUser> _siteUser = this.userRepository.findByUsername(username);
        if (_siteUser.isEmpty()) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }
        SiteUser siteUser = _siteUser.get();
        return new User(siteUser.getUsername(), siteUser.getPassword(), new ArrayList<>());
    }

    public Map<String, Object> authenticateUser(Map<String, String> loginRequest) {
        Map<String, Object> resultMap = new HashMap<>();
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        try {
            UserDetails userDetails = loadUserByUsername(username);
            if (userDetails == null || !passwordEncoder.matches(password, userDetails.getPassword())) {
                throw new RuntimeException("Invalid username or password");
            }
            String nickname = getUserNickname(username);
            resultMap.put("message", "Login successful!");
            resultMap.put("nickname", nickname);
        } catch (Exception e) {
            resultMap.put("error", "Login failed. Invalid username or password.");
        }
        return resultMap;
    }

    public String getUserNickname(String username) {
        Optional<SiteUser> user = userRepository.findByUsername(username);
        return user.map(SiteUser::getNickname).orElse(null);
    }
}





