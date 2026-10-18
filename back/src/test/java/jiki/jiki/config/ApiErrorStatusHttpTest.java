package jiki.jiki.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jiki.jiki.friend.*;
import jiki.jiki.promise.Promise;
import jiki.jiki.promise.PromiseRepository;
import jiki.jiki.security.JwtProvider;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-error-status;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "settlement.account.initialize=true", "settlement.account.existing-user-id=#{null}",
        "jwt.secret=cGVyZm9ybWFuY2UtdGVzdC1vbmx5LWtleS0zMmJ5dGVzISE=", "jwt.expiration=3600000"
})
@AutoConfigureMockMvc
@Transactional
class ApiErrorStatusHttpTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired FriendRepository friends;
    @Autowired PromiseRepository promises;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtProvider jwt;
    @Autowired ObjectMapper mapper;

    @Test void allInvalidCredentialsReturnTheSame401Response() throws Exception {
        member("member");
        String first = null;
        for (String username : new String[]{"missing", "member", "admin"}) {
            String response = mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("username", username, "password", "wrong"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("error").value("Invalid username or password"))
                    .andReturn().getResponse().getContentAsString();
            if (first == null) first = response;
            assertEquals(first, response);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"username\":\"member\"}", "{\"password\":\"password\"}",
            "{\"username\":\" \",\"password\":\"password\"}",
            "{\"username\":\"member\",\"password\":\" \"}", "null", "{"})
    void missingBlankOrMalformedLoginIs400(String body) throws Exception {
        mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "nickname", "email"})
    void duplicateSignupFieldsAre409AndDoNotCreateUsers(String field) throws Exception {
        member("existing");
        Map<String, String> body = new HashMap<>(Map.of("username", "new-member", "password1", "password",
                "nickname", "New member", "email", "new@example.test"));
        body.put(field, field.equals("email") ? "existing@example.test" : "existing");
        mvc.perform(post("/user/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
        assertEquals(2, users.count()); // the existing member and the system account
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "password1", "nickname", "email"})
    void blankSignupFieldsAre400(String field) throws Exception {
        Map<String, String> body = new HashMap<>(Map.of("username", "new-member", "password1", "password",
                "nickname", "New member", "email", "new@example.test"));
        body.put(field, " ");
        mvc.perform(post("/user/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertEquals(1, users.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "decline"})
    void friendPermissionStateAndMissingResourceAreDistinguished(String action) throws Exception {
        SiteUser sender = member("sender"), recipient = member("recipient");
        Friend request = new Friend();
        request.setUser1(sender); request.setUser2(recipient); request.setStatus(FriendStatus.PENDING);
        friends.saveAndFlush(request);
        String path = "/friend/" + action + "/" + request.getId();
        mvc.perform(post(path).header("Authorization", bearer("sender")))
                .andExpect(status().isForbidden());
        assertEquals(FriendStatus.PENDING, friends.findById(request.getId()).orElseThrow().getStatus());
        mvc.perform(post(path).header("Authorization", bearer("recipient")))
                .andExpect(action.equals("accept") ? status().isOk() : status().isNoContent());
        mvc.perform(post(path).header("Authorization", bearer("recipient")))
                .andExpect(status().isConflict());
        mvc.perform(post("/friend/" + action + "/999999").header("Authorization", bearer("recipient")))
                .andExpect(status().isNotFound());
    }

    @Test void missingFriendUserIs404AndSelfRequestIs400() throws Exception {
        member("member");
        mvc.perform(post("/friend").header("Authorization", bearer("member"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username2\":\"missing\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/friend").header("Authorization", bearer("member"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username2\":\"member\"}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"detail", "invite", "delete"})
    void promisePermissionsReturn403(String action) throws Exception {
        SiteUser owner = member("owner");
        member("outsider");
        Promise promise = new Promise();
        promise.setCreator(owner); promise.setDate("2099-01-01"); promise.setTime("12:00");
        promise.setParticipationDeadline(LocalDateTime.of(2099, 1, 1, 11, 0));
        promises.saveAndFlush(promise);
        var request = switch (action) {
            case "detail" -> get("/promise/" + promise.getId());
            case "delete" -> delete("/promise/" + promise.getId());
            default -> post("/promise/invitation").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("promiseId", promise.getId(), "guestUsername", "owner")));
        };
        mvc.perform(request.header("Authorization", bearer("outsider"))).andExpect(status().isForbidden());
        assertTrue(promises.existsById(promise.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/friend", "/promise/invitation", "/promise/update-late-status", "/settlement/reward"})
    void missingRequiredBusinessFieldsAre400(String path) throws Exception {
        member("member");
        mvc.perform(post(path).header("Authorization", bearer("member"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void preservesFrameworkErrorStatusesAndAuthenticationEntryPoint() throws Exception {
        member("member");
        mvc.perform(get("/promise")).andExpect(status().isUnauthorized());
        mvc.perform(get("/promise/not-a-number").header("Authorization", bearer("member")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/missing-resource").header("Authorization", bearer("member")))
                .andExpect(status().isNotFound());
        mvc.perform(put("/user/signup")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/user/login").contentType(MediaType.TEXT_PLAIN).content("login"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private SiteUser member(String username) {
        SiteUser user = new SiteUser();
        user.setUsername(username); user.setNickname(username); user.setEmail(username + "@example.test");
        user.setPassword(passwords.encode("password"));
        return users.saveAndFlush(user);
    }

    private String bearer(String username) {
        return "Bearer " + jwt.generateToken(username);
    }
}
