package com.example.eduhub.auth;

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
public class Employee {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String employeeNo;

  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  private EmployeeRole role;

  private Instant createdAt;

  protected Employee() {}

  public Employee(String employeeNo, String passwordHash, EmployeeRole role, Instant createdAt) {
    this.employeeNo = employeeNo;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getEmployeeNo() {
    return employeeNo;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public EmployeeRole getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** 비밀번호 해시는 로그에 남지 않도록 포함하지 않는다. */
  @Override
  public String toString() {
    return "Employee{id=" + id + ", employeeNo=" + employeeNo + ", role=" + role + "}";
  }
}
