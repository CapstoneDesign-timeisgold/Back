package jiki.jiki.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import jiki.jiki.security.JwtProvider;
import jiki.jiki.user.SystemAccountType;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:system-account-http;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "settlement.account.initialize=true", "settlement.account.existing-user-id=#{null}",
        "jwt.secret=cGVyZm9ybWFuY2UtdGVzdC1vbmx5LWtleS0zMmJ5dGVzISE=", "jwt.expiration=3600000"
})
@AutoConfigureMockMvc
class SettlementAccountHttpTest {
    @Autowired MockMvc mvc;
    @Autowired SettlementAccountService accounts;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;
    @Autowired ObjectMapper mapper;

    @Test void startupCreatesProtectedAccountAndMembersCanStillSignupAndLogin() throws Exception {
        var account = accounts.getAccount();
        assertEquals(SystemAccountType.SETTLEMENT, account.getSystemAccountType());
        accounts.initialize(null);
        assertEquals(account.getId(), accounts.getAccount().getId());
        assertEquals(1, users.count());

        mvc.perform(post("/user/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"ADMIN","password1":"password","nickname":"attacker","email":"attacker@example.test"}
                """)).andExpect(status().isBadRequest());
        mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"admin","password":"local-admin-change-me"}
                """)).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/money").header("Authorization", "Bearer " + jwt.generateToken("admin")))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/user/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"member","password1":"password","nickname":"Member","email":"member@example.test",
                 "systemAccountType":"SETTLEMENT"}
                """)).andExpect(status().isCreated());
        assertNull(users.findByUsername("member").orElseThrow().getSystemAccountType());
        String login = mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"member","password":"password"}
                """)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(login).get("token").asText();
        mvc.perform(get("/user/money").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("money").value(0));
    }
}
