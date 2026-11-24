package com.example.eduhub.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationRepository extends JpaRepository<Notification, Long> {

  List<Notification> findByEmployeeIdOrderByIdAsc(String employeeId);
}
