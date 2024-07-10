package jiki.jiki.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jiki.jiki.domain.Participant;
import jiki.jiki.domain.Promise;
import jiki.jiki.domain.SiteUser;
import jiki.jiki.dto.RewardDto;
import jiki.jiki.repository.ParticipantRepository;
import jiki.jiki.repository.PromiseRepository;
import jiki.jiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PromiseRepository promiseRepository;
    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;

    @Transactional
    public void decideRewards(RewardDto rewardDto) {
        Promise promise = promiseRepository.findById(rewardDto.getPromiseId())
                .orElseThrow(() -> new EntityNotFoundException("Invalid promise ID: " + rewardDto.getPromiseId()));

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

        int totalPenalty = lateParticipants.size() * promise.getPenalty();

        // 모두가 늦었을 때
        if (onTimeParticipants.isEmpty()) {
            if (!lateParticipants.isEmpty()) {
                for (Participant lateParticipant : lateParticipants) {
                    SiteUser lateUser = lateParticipant.getGuest();
                    lateUser.setMoney(lateUser.getMoney() - promise.getPenalty());
                    userRepository.save(lateUser);
                }
            }
            promise.setSettled(true);
            promiseRepository.save(promise);
            return;
        }

        // 모두가 시간을 지켰을 때
        if (lateParticipants.isEmpty()) {
            promise.setSettled(true);
            promiseRepository.save(promise);
            return;
        }

        // 일부만 지켰을 때
        int rewardPerParticipant = totalPenalty / onTimeParticipants.size();

        for (Participant lateParticipant : lateParticipants) {
            SiteUser lateUser = lateParticipant.getGuest();
            lateUser.setMoney(lateUser.getMoney() - promise.getPenalty());
            userRepository.save(lateUser);
        }

        for (Participant onTimeParticipant : onTimeParticipants) {
            SiteUser onTimeUser = onTimeParticipant.getGuest();
            onTimeUser.setMoney(onTimeUser.getMoney() + rewardPerParticipant);
            userRepository.save(onTimeUser);
        }

        promise.setSettled(true);
        promiseRepository.save(promise);
    }
}