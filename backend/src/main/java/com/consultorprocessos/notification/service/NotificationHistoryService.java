package com.consultorprocessos.notification.service;

import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.auth.repository.UserRepository;
import com.consultorprocessos.notification.entity.NotificationHistory;
import com.consultorprocessos.notification.repository.NotificationHistoryRepository;
import com.consultorprocessos.process.repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationHistoryService {

    private final NotificationHistoryRepository repository;
    private final ProcessRepository             processRepository; 

    @Async
    @Transactional
    public void record(User user, UUID processId,
                       String channelCode, String eventType,
                       String status, String errorMessage) {
        try {
            NotificationHistory history = new NotificationHistory();
            history.setUser(user);

            if (processId != null) {
                history.setProcess(processRepository.getReferenceById(processId));
            }

            history.setChannel(channelCode);
            history.setEventType(eventType);
            history.setStatus(status);
            history.setErrorMessage(truncate(errorMessage, 500));
            repository.save(history);

        } catch (Exception e) {
            log.error("Falha ao gravar notification_history: userId={} canal={} erro={}",
                    user.getId(), channelCode, e.getMessage());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}