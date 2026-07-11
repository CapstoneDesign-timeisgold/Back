package jiki.jiki.settlement;

import jakarta.persistence.EntityNotFoundException;
import jiki.jiki.promise.Participant;
import jiki.jiki.promise.Promise;
import jiki.jiki.promise.PromiseRepository;
import jiki.jiki.user.SiteUser;
import jiki.jiki.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final PromiseRepository promiseRepository;
    private final UserRepository userRepository;
    private final MoneyRecordRepository moneyRecordRepository;

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
    public PromiseResultDto getPromiseResultDetails(Long promiseId) {
        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + promiseId));

        if (!promise.isSettled()) {
            throw new IllegalStateException("Promise settlement has not been finalized yet");
        }

        Set<Participant> participants = promise.getParticipants();

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

    // 개인 정산 기록 저장
    private void saveRecord(Promise promise, List<UserPenaltyDto> lateUsers, List<UserPenaltyDto> onTimeUsers) {
        lateUsers.forEach(lateUserDto -> {
            SiteUser lateUser = userRepository.findByUsername(lateUserDto.getUsername())
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + lateUserDto.getUsername()));

            int penaltyAmount = lateUserDto.getPenaltyAmount();
            int updatedBalance = lateUser.getMoney() - penaltyAmount;
            lateUser.setMoney(updatedBalance);
            userRepository.save(lateUser);

            MoneyRecord penaltyRecord = new MoneyRecord();
            penaltyRecord.setPromiseTitle(promise.getTitle());
            penaltyRecord.setAmount(penaltyAmount);
            penaltyRecord.setPenalty(true); // 벌금 적용
            penaltyRecord.setTransactionDate(LocalDateTime.now());
            penaltyRecord.setBalanceAfterTransaction(updatedBalance);
            penaltyRecord.setUser(lateUser);
            moneyRecordRepository.save(penaltyRecord);
        });

        // 보상을 적용하여 기록
        onTimeUsers.forEach(onTimeUserDto -> {
            SiteUser onTimeUser = userRepository.findByUsername(onTimeUserDto.getUsername())
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + onTimeUserDto.getUsername()));

            int updatedBalance = onTimeUser.getMoney() + onTimeUserDto.getRewardAmount();
            onTimeUser.setMoney(updatedBalance);
            userRepository.save(onTimeUser);

            MoneyRecord rewardRecord = new MoneyRecord();
            rewardRecord.setPromiseTitle(promise.getTitle());
            rewardRecord.setAmount(onTimeUserDto.getRewardAmount());
            rewardRecord.setPenalty(false); // 보상 적용
            rewardRecord.setTransactionDate(LocalDateTime.now());
            rewardRecord.setBalanceAfterTransaction(updatedBalance);
            rewardRecord.setUser(onTimeUser);
            moneyRecordRepository.save(rewardRecord);
        });
    }

    // 약속이 끝났을 때 정산 기능
    @Transactional
    public PromiseResultDto decideRewards(RewardDto rewardDto) {
        Long promiseId = rewardDto.getPromiseId();
        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + promiseId));

        if (promise.isSettled()) {
            throw new IllegalStateException("Rewards have already been settled for this promise");
        }

        Set<Participant> participants = promise.getParticipants();
        List<Participant> lateParticipants = participants.stream()
                .filter(participant -> !participant.isArrival())
                .collect(Collectors.toList());

        List<Participant> onTimeParticipants = participants.stream()
                .filter(Participant::isArrival)
                .collect(Collectors.toList());

        int penaltyPerUser = promise.getPenalty();
        int totalPenalty = lateParticipants.size() * penaltyPerUser;

        List<UserPenaltyDto> lateUserDtos;
        List<UserPenaltyDto> onTimeUserDtos;

        if (onTimeParticipants.isEmpty()) {
            // 모든 사용자가 지각한 경우 벌금을 admin에게 분배
            lateUserDtos = distributePenaltyToAdmin(lateParticipants, totalPenalty, promise);
            onTimeUserDtos = List.of(); // 지킨 사용자가 없음
        } else {
            // 지각한 사용자에 대해 벌금 분배
            lateUserDtos = lateParticipants.stream()
                    .map(participant -> new UserPenaltyDto(participant.getGuest().getUsername(), penaltyPerUser, 0))
                    .collect(Collectors.toList());

            // 시간 지킨 사용자에게 보상 분배
            int rewardPerParticipant = totalPenalty / onTimeParticipants.size();
            onTimeUserDtos = onTimeParticipants.stream()
                    .map(participant -> new UserPenaltyDto(participant.getGuest().getUsername(), 0, rewardPerParticipant))
                    .collect(Collectors.toList());
        }

        promise.setSettled(true);
        promiseRepository.save(promise);

        saveRecord(promise, lateUserDtos, onTimeUserDtos);

        return PromiseResultDto.builder()
                .promiseId(promise.getId())
                .lateUsers(lateUserDtos)
                .onTimeUsers(onTimeUserDtos)
                .totalPenalty(totalPenalty)
                .build();
    }

    // 벌금을 관리자로 전송하고, 벌금 사용자 목록 반환
    private List<UserPenaltyDto> distributePenaltyToAdmin(List<Participant> lateParticipants, int totalPenalty, Promise promise) {
        SiteUser admin = userRepository.findByUsername("admin")
                .orElseThrow(() -> new EntityNotFoundException("Admin user not found"));

        List<UserPenaltyDto> lateUserDtos = lateParticipants.stream()
                .map(participant -> new UserPenaltyDto(participant.getGuest().getUsername(), promise.getPenalty(), 0))
                .collect(Collectors.toList());

        admin.setMoney(admin.getMoney() + totalPenalty);
        userRepository.save(admin);

        return lateUserDtos;
    }
}
