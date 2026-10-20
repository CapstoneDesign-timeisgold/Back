package jiki.jiki.promise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jiki.jiki.security.JwtProvider;
import jiki.jiki.settlement.SettlementService;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:arrival-http;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "settlement.account.initialize=true", "settlement.account.existing-user-id=#{null}",
        "jwt.secret=cGVyZm9ybWFuY2UtdGVzdC1vbmx5LWtleS0zMmJ5dGVzISE=", "jwt.expiration=3600000"
})
@AutoConfigureMockMvc
@Transactional
class ArrivalHttpTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 12, 30);
    @MockBean Clock clock;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtProvider jwt;
    @Autowired UserRepository users;
    @Autowired PromiseRepository promises;
    @Autowired ParticipantRepository participants;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired SettlementService settlements;
    private LocalDateTime now;
    private Promise promise;
    private Long guestId;

    @BeforeEach void setup() {
        now = START.minusMinutes(10);
        when(clock.getZone()).thenReturn(SEOUL);
        when(clock.instant()).thenAnswer(call -> now.atZone(SEOUL).toInstant());
        SiteUser host = member("host"), guest = member("guest"), absent = member("absent");
        promise = new Promise();
        promise.setCreator(host); promise.setDate("2026-10-20"); promise.setTime("12:30");
        promise.setTitle("arrival test"); promise.setPenalty(1000); promise.setParticipationDeadline(START);
        promises.saveAndFlush(promise);
        addParticipant(host); guestId = addParticipant(guest).getId(); addParticipant(absent);
        reload();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-20T12:20:00", "2026-10-20T12:29:59.999999", "2026-10-20T12:30:00"})
    void confirmsOnTimeFromOpeningThroughExactStart(String time) throws Exception {
        now = LocalDateTime.parse(time);
        confirm("guest", 204);
        reload();
        Participant saved = participants.findById(guestId).orElseThrow();
        assertTrue(saved.isArrival()); assertTrue(saved.isOnTime());
        assertEquals(now, saved.getArrivedAt());
        JsonNode detail = detail();
        assertTrue(detail.get("arrivalConfirmationOpen").asBoolean());
        assertEquals(START.minusMinutes(10), LocalDateTime.parse(detail.get("arrivalOpensAt").asText()));
        assertEquals("ON_TIME", guestDetail(detail).get("arrivalStatus").asText());
    }

    @Test void rejectsOneMicrosecondBeforeOpeningWithoutWritingArrival() throws Exception {
        now = START.minusMinutes(10).minusNanos(1000);
        confirm("guest", 409);
        reload();
        assertFalse(participants.findById(guestId).orElseThrow().isArrival());
        assertNull(participants.findById(guestId).orElseThrow().getArrivedAt());
        assertFalse(detail().get("arrivalConfirmationOpen").asBoolean());
    }

    @Test void lateConfirmationUsesServerTimeAndCannotEarnOnTimeReward() throws Exception {
        now = START;
        confirm("host", 204);
        now = START.plusNanos(1000);
        mvc.perform(post("/promise/update-late-status").header("Authorization", bearer("guest"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("promiseId", promise.getId(), "arrival", true,
                                "arrivedAt", "2020-01-01T00:00:00", "username", "absent"))))
                .andExpect(status().isNoContent());
        reload();
        Participant late = participants.findById(guestId).orElseThrow();
        assertTrue(late.isArrival()); assertFalse(late.isOnTime());
        assertEquals(now, late.getArrivedAt());
        JsonNode lateDetail = guestDetail(detail());
        assertEquals("LATE", lateDetail.get("arrivalStatus").asText());
        assertEquals(now, LocalDateTime.parse(lateDetail.get("arrivedAt").asText()));
        assertFalse(participants.findByPromiseIdAndGuestUsername(promise.getId(), "absent").orElseThrow().isArrival());

        mvc.perform(post("/settlement/reward").header("Authorization", bearer("host"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"promiseId\":" + promise.getId() + "}"))
                .andExpect(status().isNoContent());
        var result = settlements.getPromiseResultDetails(promise.getId(), "host");
        assertEquals(2000, result.getTotalPenalty());
        assertEquals(1, result.getOnTimeUsers().size());
        assertEquals("host", result.getOnTimeUsers().get(0).getUsername());
        assertEquals(2000, result.getOnTimeUsers().get(0).getRewardAmount());
        assertEquals(9000, money("guest")); assertEquals(9000, money("absent")); assertEquals(12000, money("host"));
        assertEquals(3L, jdbc.queryForObject("select count(*) from money_record where promise_id=?", Long.class, promise.getId()));

        // A retry must not change the original confirmation time or apply transfers again.
        now = START.plusMinutes(5);
        confirm("guest", 409);
        confirm("absent", 409);
        reload();
        assertEquals(START.plusNanos(1000), participants.findById(guestId).orElseThrow().getArrivedAt());
        assertFalse(detail().get("arrivalConfirmationOpen").asBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-20T12:20:00", "2026-10-20T12:31:00"})
    void rejectsRepeatedConfirmationBeforeAndAfterStart(String time) throws Exception {
        now = LocalDateTime.parse(time);
        confirm("guest", 204);
        LocalDateTime original = now;
        now = now.plusMinutes(1);
        confirm("guest", 409);
        reload();
        assertEquals(original, participants.findById(guestId).orElseThrow().getArrivedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"arrival\":false}"})
    void missingOrFalseArrivalIs400(String body) throws Exception {
        JsonNode fields = mapper.readTree(body);
        var request = mapper.createObjectNode().put("promiseId", promise.getId());
        if (fields.has("arrival")) request.put("arrival", fields.get("arrival").asBoolean());
        mvc.perform(post("/promise/update-late-status").header("Authorization", bearer("guest"))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest());
        reload();
        assertNull(participants.findById(guestId).orElseThrow().getArrivedAt());
    }

    @Test void preservesHistoricalConfirmationsWithoutInventingATimestamp() throws Exception {
        Participant legacy = participants.findById(guestId).orElseThrow();
        legacy.setArrival(true);
        reload();
        now = START.plusMinutes(1);
        confirm("guest", 409);
        assertTrue(participants.findById(guestId).orElseThrow().isOnTime());
        JsonNode detail = guestDetail(detail());
        assertEquals("ON_TIME", detail.get("arrivalStatus").asText());
        assertTrue(detail.get("arrivedAt").isNull());
    }

    @Test void cancellationAndReinvitationResetConfirmation() throws Exception {
        confirm("guest", 204);
        mvc.perform(post("/promise/cancel/" + guestId).header("Authorization", bearer("guest")))
                .andExpect(status().isNoContent());
        reload();
        assertNull(participants.findById(guestId).orElseThrow().getArrivedAt());
        assertEquals(ArrivalStatus.NOT_CONFIRMED, participants.findById(guestId).orElseThrow().getArrivalStatus());
        mvc.perform(post("/promise/invitation").header("Authorization", bearer("host"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("promiseId", promise.getId(), "guestUsername", "guest"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/promise/accept/" + guestId).header("Authorization", bearer("guest")))
                .andExpect(status().isOk());
        now = now.plusMinutes(1);
        confirm("guest", 204);
        reload();
        assertEquals(now, participants.findById(guestId).orElseThrow().getArrivedAt());
    }

    @Test void authenticatedNonParticipantCannotConfirmSomeoneElsesArrival() throws Exception {
        member("outsider");
        confirm("outsider", 404);
        reload();
        assertNull(participants.findById(guestId).orElseThrow().getArrivedAt());
    }

    private void confirm(String username, int expectedStatus) throws Exception {
        mvc.perform(post("/promise/update-late-status").header("Authorization", bearer(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("promiseId", promise.getId(), "arrival", true))))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode detail() throws Exception {
        String response = mvc.perform(get("/promise/" + promise.getId()).header("Authorization", bearer("host")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private JsonNode guestDetail(JsonNode detail) {
        for (JsonNode participant : detail.get("participants")) {
            if (participant.get("username").asText().equals("guest")) return participant;
        }
        throw new AssertionError("Guest missing from response");
    }

    private SiteUser member(String name) {
        SiteUser user = new SiteUser();
        user.setUsername(name); user.setNickname(name); user.setEmail(name + "@example.test");
        user.setPassword("unused"); user.setMoney(10000);
        return users.saveAndFlush(user);
    }

    private Participant addParticipant(SiteUser guest) {
        Participant participant = new Participant();
        participant.setPromise(promise); participant.setHost(promise.getCreator()); participant.setGuest(guest);
        participant.setStatus(ParticipantStatus.ACCEPTED);
        return participants.saveAndFlush(participant);
    }

    private int money(String name) {
        return jdbc.queryForObject("select money from site_user where username=?", Integer.class, name);
    }

    private String bearer(String username) { return "Bearer " + jwt.generateToken(username); }
    private void reload() { em.flush(); em.clear(); }
}
