package jiki.jiki.settlement;

import jiki.jiki.config.ConflictException;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.SystemAccountType;
import jiki.jiki.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementAccountService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public SiteUser getAccount() {
        return users.findBySystemAccountType(SystemAccountType.SETTLEMENT)
                .orElseThrow(() -> new ConflictException("Settlement system account is not initialized"));
    }

    @Transactional
    public void initialize(Long existingUserId) {
        if (existingUserId != null && existingUserId <= 0) {
            throw new IllegalStateException("settlement.account.existing-user-id must be positive");
        }
        SiteUser initialized = users.findBySystemAccountType(SystemAccountType.SETTLEMENT).orElse(null);
        if (initialized != null) {
            if (existingUserId != null && !initialized.getId().equals(existingUserId)) {
                throw new IllegalStateException("Configured settlement account differs from the initialized account");
            }
            return;
        }

        SiteUser account;
        if (existingUserId != null) {
            // An operator must explicitly verify ownership before adopting an existing account.
            account = users.findById(existingUserId)
                    .orElseThrow(() -> new IllegalStateException("Configured settlement account does not exist"));
        } else {
            String username = SystemAccountType.SETTLEMENT.getReservedUsername();
            if (users.findByUsernameIgnoreCase(username).isPresent()) {
                throw new IllegalStateException("Existing admin account requires ownership verification and "
                        + "an explicit settlement.account.existing-user-id; it will not be adopted automatically");
            }
            account = new SiteUser();
            account.setUsername(username);
            String suffix = UUID.randomUUID().toString();
            account.setNickname("settlement-" + suffix);
            account.setEmail("settlement-" + suffix + "@jiki.invalid");
            account.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        }
        account.setSystemAccountType(SystemAccountType.SETTLEMENT);
        users.saveAndFlush(account);
    }
}
