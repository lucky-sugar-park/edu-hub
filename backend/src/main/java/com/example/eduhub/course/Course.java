package com.example.eduhub.course;

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

  public void close() {
    this.status = CourseStatus.CLOSED;
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
