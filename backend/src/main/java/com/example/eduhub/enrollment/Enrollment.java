package com.example.eduhub.enrollment;

import com.example.eduhub.course.CourseSession;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class Enrollment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id")
  private CourseSession session;

  private String employeeId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  private EnrollmentStatus status;

  private Instant appliedAt;

  protected Enrollment() {}

  public Enrollment(
      CourseSession session, String employeeId, EnrollmentStatus status, Instant appliedAt) {
    this.session = session;
    this.employeeId = employeeId;
    this.status = status;
    this.appliedAt = appliedAt;
  }

  public Long getId() {
    return id;
  }

  public CourseSession getSession() {
    return session;
  }

  public String getEmployeeId() {
    return employeeId;
  }

  public EnrollmentStatus getStatus() {
    return status;
  }

  public Instant getAppliedAt() {
    return appliedAt;
  }
}
