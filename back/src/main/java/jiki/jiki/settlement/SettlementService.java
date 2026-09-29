package jiki.jiki.settlement;

import jakarta.persistence.EntityNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jiki.jiki.config.ConflictException;
import jiki.jiki.promise.Participant;
import jiki.jiki.promise.ParticipantStatus;
import jiki.jiki.promise.Promise;
import jiki.jiki.promise.PromiseRepository;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final PromiseRepository promiseRepository;
    private final UserRepository userRepository;
    private final MoneyRecordRepository moneyRecordRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    // 개인 포인트 조회
    @Transactional(readOnly = true)
    public MoneyDto getUserMoney(String username) {
        SiteUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
        return MoneyDto.builder().money(user.getMoney()).build();
    }

    // "admin"의 총 금액(전체 모금액)
    @Transactional(readOnly = true)
    public MoneyDto getAdminMoney() {
        SiteUser admin = userRepository.findByUsername("admin")
                .orElseThrow(() -> new EntityNotFoundException("Admin user not found"));
        return MoneyDto.builder().money(admin.getMoney()).build();
    }

    // 개인 거래 내역(마치 은행 개인 계좌 거래 내역처럼)을 사용자가 확인할 수 있게 하기
    @Transactional(readOnly = true)
    public List<MoneyRecordDto> getUserMoneyRecords(String username) {
        SiteUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));

        return moneyRecordRepository.findByUser(user).stream()
                .map(record -> MoneyRecordDto.builder()
                        .id(record.getId())
                        .promiseTitle(record.getPromiseTitle())
                        .amount(record.getAmount())
                        .isPenalty(record.isPenalty())
                        .transactionDate(record.getTransactionDate())
                        .balanceAfterTransaction(record.getBalanceAfterTransaction())
                        .build())
                .collect(Collectors.toList());
    }

    // 약속 정산 결과를 계산하고 정산 결과를 확인 할 수 있는 기능
    @Transactional
    public PromiseResultDto getPromiseResultDetails(Long promiseId, String username) {
        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + promiseId));

        validateResultViewer(promise, username);

        if (!promise.isSettled()) {
            throw new ConflictException("Promise settlement has not been finalized yet");
        }

        if (promise.getSettlementResult() != null) return readResult(promise);

        Set<Participant> participants = acceptedParticipants(promise);

        // 지각자
        List<UserPenaltyDto> lateUsers = participants.stream()
                .filter(participant -> !participant.isArrival())
                .map(participant -> {
                    SiteUser lateUser = participant.getGuest();
                    return UserPenaltyDto.builder()
                            .username(lateUser.getUsername())
                            .penaltyAmount(promise.getPenalty())
                            .rewardAmount(0) // 벌금만 적용, 보상 없음
                            .build();
                }).collect(Collectors.toList());

        int totalPenalty = lateUsers.size() * promise.getPenalty();

        int rewardPerParticipant = totalPenalty / Math.max(1, participants.size() - lateUsers.size());

        List<UserPenaltyDto> onTimeUsers = participants.stream()
                .filter(Participant::isArrival)
                .map(participant -> {
                    SiteUser onTimeUser = participant.getGuest();
                    return UserPenaltyDto.builder()
                            .username(onTimeUser.getUsername())
                            .penaltyAmount(0)  // 보상만 적용, 벌금 없음
                            .rewardAmount(rewardPerParticipant)
                            .build();
                }).collect(Collectors.toList());

        return PromiseResultDto.builder()
                .promiseId(promise.getId())
                .lateUsers(lateUsers)
                .onTimeUsers(onTimeUsers)
                .totalPenalty(totalPenalty)
                .build();
    }

    // The promise ID is the idempotency key: settlement is a once-only operation.
    @Transactional
    public PromiseResultDto decideRewards(String username, RewardDto rewardDto) {
        if (rewardDto == null || rewardDto.getPromiseId() == null) {
            throw new IllegalArgumentException("Promise ID is required");
        }
        Promise promise = promiseRepository.findForUpdateById(rewardDto.getPromiseId())
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + rewardDto.getPromiseId()));
        if (!promise.getCreator().getUsername().equals(username)) {
            throw new AccessDeniedException("Only the promise creator can settle rewards");
        }
        if (promise.isSettled()) {
            if (promise.getSettlementResult() == null) {
                throw new ConflictException("Legacy settlement has no saved response; use the result endpoint");
            }
            return readResult(promise);
        }
        validatePromiseHasEnded(promise);
        if (promise.getPenalty() < 0) throw new ConflictException("Penalty must not be negative");

        List<Participant> participants = acceptedParticipants(promise).stream()
                .sorted(Comparator.comparing(p -> p.getGuest().getId())).toList();
        if (participants.isEmpty()) throw new ConflictException("No accepted participants to settle");
        List<Participant> late = participants.stream().filter(p -> !p.isArrival()).toList();
        List<Participant> onTime = participants.stream().filter(Participant::isArrival).toList();
        int total = checkedMultiply(late.size(), promise.getPenalty());
        int reward = onTime.isEmpty() ? 0 : total / onTime.size();
        int adminAmount = onTime.isEmpty() ? total : total % onTime.size();
        Map<Long, SiteUser> accounts = new TreeMap<>();
        participants.forEach(p -> accounts.put(p.getGuest().getId(), p.getGuest()));
        SiteUser admin = null;
        if (adminAmount > 0) {
            admin = userRepository.findByUsername("admin")
                    .orElseThrow(() -> new ConflictException("Settlement requires an admin account"));
            accounts.put(admin.getId(), admin);
        }

        // Lock all affected balances in ascending ID order BEFORE any balance/record writes.
        // Native scalar reads see current DB balances even when the creator/guest was cached.
        Map<Long, Integer> balances = new HashMap<>();
        for (Long id : accounts.keySet()) {
            balances.put(id, userRepository.lockBalanceById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Invalid account: " + id)));
        }
        LocalDateTime settledAt = LocalDateTime.now(clock);
        for (Participant p : late) applyTransfer(promise, p.getGuest(), promise.getPenalty(), true, settledAt, balances);
        boolean adminRewardRecorded = false;
        for (Participant p : onTime) {
            boolean isAdmin = admin != null && p.getGuest().getId().equals(admin.getId());
            applyTransfer(promise, p.getGuest(), reward + (isAdmin ? adminAmount : 0), false, settledAt, balances);
            adminRewardRecorded |= isAdmin;
        }
        if (adminAmount > 0 && !adminRewardRecorded) applyTransfer(promise, admin, adminAmount, false, settledAt, balances);

        PromiseResultDto result = PromiseResultDto.builder().promiseId(promise.getId())
                .lateUsers(late.stream().map(p -> new UserPenaltyDto(p.getGuest().getUsername(), promise.getPenalty(), 0)).toList())
                .onTimeUsers(onTime.stream().map(p -> new UserPenaltyDto(p.getGuest().getUsername(), 0, reward)).toList())
                .totalPenalty(total).adminAmount(adminAmount).build();
        try { promise.setSettlementResult(objectMapper.writeValueAsString(result)); }
        catch (JsonProcessingException e) { throw new RuntimeException("Cannot save settlement result", e); }
        promise.setSettled(true);
        return result;
    }

    private void applyTransfer(Promise promise, SiteUser user, int amount, boolean penalty,
                               LocalDateTime settledAt, Map<Long, Integer> balances) {
        // A zero-value on-time reward remains a recorded settlement outcome.
        int updated;
        try { updated = Math.addExact(balances.get(user.getId()), penalty ? -amount : amount); }
        catch (ArithmeticException e) { throw new ConflictException("Account balance exceeds supported range"); }
        if (userRepository.updateLockedBalance(user.getId(), updated) != 1) {
            throw new EntityNotFoundException("Invalid account: " + user.getId());
        }
        balances.put(user.getId(), updated);
        MoneyRecord record = new MoneyRecord();
        record.setPromiseId(promise.getId()); record.setPromiseTitle(promise.getTitle());
        record.setUser(user); record.setAmount(amount); record.setPenalty(penalty);
        record.setTransactionDate(settledAt); record.setBalanceAfterTransaction(updated);
        moneyRecordRepository.save(record);
    }

    private int checkedMultiply(int count, int penalty) {
        try { return Math.multiplyExact(count, penalty); }
        catch (ArithmeticException e) { throw new ConflictException("Total penalty exceeds supported range"); }
    }

    private PromiseResultDto readResult(Promise promise) {
        try { return objectMapper.readValue(promise.getSettlementResult(), PromiseResultDto.class); }
        catch (JsonProcessingException e) { throw new RuntimeException("Cannot read saved settlement result", e); }
    }
    private void validateResultViewer(Promise promise, String username) {
        boolean isCreator = promise.getCreator().getUsername().equals(username);
        boolean isAcceptedParticipant = promise.getParticipants().stream()
                .anyMatch(participant -> participant.getStatus() == ParticipantStatus.ACCEPTED
                        && participant.getGuest().getUsername().equals(username));

        if (!isCreator && !isAcceptedParticipant) {
            throw new AccessDeniedException("User not authorized to view this settlement result");
        }
    }

    private Set<Participant> acceptedParticipants(Promise promise) {
        return promise.getParticipants().stream()
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACCEPTED)
                .collect(Collectors.toSet());
    }

    private void validatePromiseHasEnded(Promise promise) {
        try {
            LocalDateTime promiseDateTime = LocalDateTime.of(
                    LocalDate.parse(promise.getDate()),
                    LocalTime.parse(promise.getTime())
            );

            if (LocalDateTime.now(clock).isBefore(promiseDateTime)) {
                throw new ConflictException("Promise cannot be settled before its scheduled time");
            }
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("Promise date and time must use ISO format (yyyy-MM-dd, HH:mm)");
        }
    }

}
