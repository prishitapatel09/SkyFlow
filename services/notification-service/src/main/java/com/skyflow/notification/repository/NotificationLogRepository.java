package com.skyflow.notification.repository;

import com.skyflow.notification.domain.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Page<NotificationLog> findByRecipientOrderByCreatedAtDesc(String recipient, Pageable pageable);

    Page<NotificationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
