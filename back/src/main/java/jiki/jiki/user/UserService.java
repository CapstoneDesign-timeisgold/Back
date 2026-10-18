package jiki.jiki.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import jiki.jiki.config.ConflictException;

import java.sql.SQLException;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Map<String, String> createUser(UserSignupDto userCreateForm) {
        if (SystemAccountType.isReservedUsername(userCreateForm.getUsername())) {
            throw new IllegalArgumentException("시스템 계정으로 예약된 아이디입니다.");
        }
        if (userRepository.findByUsername(userCreateForm.getUsername()).isPresent()) {
            throw new ConflictException("이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByNickname(userCreateForm.getNickname())) {
            throw new ConflictException("이미 사용 중인 닉네임입니다.");
        }
        if (userRepository.existsByEmail(userCreateForm.getEmail())) {
            throw new ConflictException("이미 사용 중인 이메일입니다.");
        }
        SiteUser user = new SiteUser();
        user.setUsername(userCreateForm.getUsername());
        user.setNickname(userCreateForm.getNickname());
        user.setEmail(userCreateForm.getEmail());
        user.setPassword(passwordEncoder.encode(userCreateForm.getPassword1()));
        try {
            // Flush here so concurrent duplicate signups are also translated to 409.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKey(e)) {
                throw new ConflictException("이미 사용 중인 아이디, 닉네임 또는 이메일입니다.");
            }
            throw e;
        }
        return Map.of("message", "User signed up successfully!");
    }

    private boolean isDuplicateKey(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                for (SQLException current = sql; current != null; current = current.getNextException()) {
                    if ("23505".equals(current.getSQLState())
                            || ("23000".equals(current.getSQLState()) && current.getErrorCode() == 1062)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}







