package jiki.jiki.settlement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "settlement.account.initialize", havingValue = "true", matchIfMissing = true)
public class SettlementAccountInitializer implements ApplicationRunner {
    private final SettlementAccountService accounts;
    private final Long existingUserId;

    public SettlementAccountInitializer(SettlementAccountService accounts,
            @Value("${settlement.account.existing-user-id:#{null}}") Long existingUserId) {
        this.accounts = accounts;
        this.existingUserId = existingUserId;
    }

    @Override
    public void run(ApplicationArguments args) {
        accounts.initialize(existingUserId);
    }
}
