package com.example.eduhub.course;

import com.example.eduhub.common.ConflictException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class Course {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;

  private String category;

  private String description;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  private CourseStatus status;

  protected Course() {}

  public Course(String title, String category, String description) {
    this.title = title;
    this.category = category;
    this.description = description;
    this.status = CourseStatus.OPEN;
  }

  public void update(String title, String category, String description) {
    ensureOpen();
    this.title = title;
    this.category = category;
    this.description = description;
  }

  /** 과정을 종료한다. 모집 종료를 뜻하며 이미 등록된 회차는 그대로 두고, 교육이 끝난 뒤 회차별로 종료한다(D-15). */
  public void close() {
    ensureOpen();
    this.status = CourseStatus.CLOSED;
  }

  /** 종료된 과정은 수정하거나 회차를 추가할 수 없다. */
  public void ensureOpen() {
    if (status == CourseStatus.CLOSED) {
      throw new ConflictException("COURSE_CLOSED", "종료된 과정입니다: " + id);
    }
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public CourseStatus getStatus() {
    return status;
  }
}
