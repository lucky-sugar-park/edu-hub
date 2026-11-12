package com.example.eduhub.course;

import com.example.eduhub.common.ConflictException;
import com.example.eduhub.common.InvalidRequestException;
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
public class CourseSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id")
  private Course course;

  private Instant startAt;

  private String place;

  private int capacity;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  private CourseSessionStatus status;

  protected CourseSession() {}

  public CourseSession(Course course, Instant startAt, String place, int capacity) {
    this.course = course;
    this.startAt = startAt;
    this.place = place;
    this.capacity = capacity;
    this.status = CourseSessionStatus.OPEN;
  }

  /** 교육 시작 시각이 지났으면(같은 시각 포함) 시작한 것으로 본다. */
  public boolean hasStarted(Instant now) {
    return !now.isBefore(startAt);
  }

  /**
   * 회차를 수정한다. 교육 시작 후에는 정원과 시작 시각을 바꿀 수 없고 장소만 바꿀 수 있다(D-08, D-17).
   *
   * <p>정원 변경에 따른 대기자 승급(D-02)과 확정자 강등(D-03)은 같은 트랜잭션에서 {@code SessionCapacityChangeHandler}가 처리한다.
   */
  public void update(Instant startAt, String place, int capacity, Instant now) {
    ensureOpen();
    if (hasStarted(now)) {
      if (!this.startAt.equals(startAt) || this.capacity != capacity) {
        throw new ConflictException("SESSION_STARTED", "교육이 시작된 회차는 정원과 시작 시각을 바꿀 수 없습니다: " + id);
      }
    } else if (startAt.isBefore(now)) {
      throw new InvalidRequestException("startAt: 현재 이후 시각이어야 합니다");
    }
    this.startAt = startAt;
    this.place = place;
    this.capacity = capacity;
  }

  /** 회차를 종료한다. 회차 종료는 교육 완료를 뜻하므로 교육 시작 후에만 할 수 있다(D-04, D-16). */
  public void close(Instant now) {
    ensureOpen();
    if (!hasStarted(now)) {
      throw new ConflictException("SESSION_NOT_STARTED", "아직 시작하지 않은 회차는 종료할 수 없습니다: " + id);
    }
    this.status = CourseSessionStatus.CLOSED;
  }

  /** 신청 가능한 회차인지 검사한다. 종료 여부를 먼저, 교육 시작 여부를 다음에 본다(D-08). */
  public void ensureEnrollable(Instant now) {
    ensureOpen();
    if (hasStarted(now)) {
      throw new ConflictException("SESSION_STARTED", "교육이 시작된 회차에는 신청할 수 없습니다: " + id);
    }
  }

  /**
   * 취소 가능한 회차인지 검사한다. 교육이 시작하면 확정·대기 모두 취소할 수 없다(D-07). 검사 순서는 {@link #ensureEnrollable}과 같다(종료 →
   * 시작).
   */
  public void ensureCancellable(Instant now) {
    ensureOpen();
    if (hasStarted(now)) {
      throw new ConflictException("SESSION_STARTED", "교육이 시작된 회차의 신청은 취소할 수 없습니다: " + id);
    }
  }

  private void ensureOpen() {
    if (status == CourseSessionStatus.CLOSED) {
      throw new ConflictException("SESSION_CLOSED", "종료된 회차입니다: " + id);
    }
  }

  public Long getId() {
    return id;
  }

  public Course getCourse() {
    return course;
  }

  public Instant getStartAt() {
    return startAt;
  }

  public String getPlace() {
    return place;
  }

  public int getCapacity() {
    return capacity;
  }

  public CourseSessionStatus getStatus() {
    return status;
  }
}
