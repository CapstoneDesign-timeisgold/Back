package jiki.jiki.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jiki.jiki.domain.SiteUser;
import jiki.jiki.dto.MoneyDto;
import jiki.jiki.dto.MoneyRecordDto;
import jiki.jiki.repository.MoneyRecordRepository;
import jiki.jiki.repository.ParticipantRepository;
import jiki.jiki.repository.PromiseRepository;
import jiki.jiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PromiseRepository promiseRepository;
    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;
    private final MoneyRecordRepository moneyRecordRepository;


    // "admin"의 총 금액(전체 모금액)
    @Transactional
    public MoneyDto getAdminMoney() {
        SiteUser admin = userRepository.findByUsername("admin")
                .orElseThrow(() -> new EntityNotFoundException("Admin user not found"));
        return MoneyDto.builder().money(admin.getMoney()).build();
    }

    //PromiseService의 saveRecord를 사용하여 개인 거래 내역(마치 은행 개인 계좌 거래 내역처럼)을 사용자가 확인할 수 있게 하기
    @Transactional
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
}