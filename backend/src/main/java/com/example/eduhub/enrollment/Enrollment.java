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

  /** 대기 순서 키. WAITING일 때만 값이 있고 작을수록 앞이다(D-23). */
  private Long waitingKey;

  /** 확정 시각. 정원 감소 시 가장 늦게 확정된 사람부터 강등하는 기준이다(D-03). */
  private Instant confirmedAt;

  protected Enrollment() {}

  private Enrollment(
      CourseSession session,
      String employeeId,
      EnrollmentStatus status,
      Instant appliedAt,
      Long waitingKey,
      Instant confirmedAt) {
    this.session = session;
    this.employeeId = employeeId;
    this.status = status;
    this.appliedAt = appliedAt;
    this.waitingKey = waitingKey;
    this.confirmedAt = confirmedAt;
  }

  public static Enrollment confirmed(CourseSession session, String employeeId, Instant now) {
    return new Enrollment(session, employeeId, EnrollmentStatus.CONFIRMED, now, null, now);
  }

  public static Enrollment waiting(
      CourseSession session, String employeeId, Instant now, long waitingKey) {
    return new Enrollment(session, employeeId, EnrollmentStatus.WAITING, now, waitingKey, null);
  }

  /** 정원 감소로 확정에서 대기로 내린다(D-03). */
  public void demote(long waitingKey) {
    if (status != EnrollmentStatus.CONFIRMED) {
      throw new IllegalStateException("확정 상태만 대기로 내릴 수 있습니다: " + status);
    }
    this.status = EnrollmentStatus.WAITING;
    this.confirmedAt = null;
    this.waitingKey = waitingKey;
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

  public Long getWaitingKey() {
    return waitingKey;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }
}
