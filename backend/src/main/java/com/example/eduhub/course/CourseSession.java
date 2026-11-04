package com.example.eduhub.course;

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

  public void close() {
    this.status = CourseSessionStatus.CLOSED;
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
