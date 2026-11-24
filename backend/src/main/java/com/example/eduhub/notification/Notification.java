package com.example.eduhub.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String employeeId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  private NotificationType type;

  private String message;

  private Instant createdAt;

  protected Notification() {}

  Notification(String employeeId, NotificationType type, String message, Instant createdAt) {
    this.employeeId = employeeId;
    this.type = type;
    this.message = message;
    this.createdAt = createdAt;
  }

  Long getId() {
    return id;
  }

  String getEmployeeId() {
    return employeeId;
  }

  NotificationType getType() {
    return type;
  }

  String getMessage() {
    return message;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
