package com.example.eduhub.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CourseRepositoryTest {

  @Autowired private CourseRepository courseRepository;

  @Autowired private TestEntityManager entityManager;

  @Test
  void savesAndFindsCourseWithSession() {
    Instant startAt = Instant.parse("2026-12-01T01:00:00Z");
    Course course = courseRepository.save(new Course("JPA 입문", "개발", "JPA 기초 과정"));
    CourseSession session =
        entityManager.persistAndFlush(new CourseSession(course, startAt, "본사 3층", 20));
    entityManager.clear();

    Course foundCourse = courseRepository.findById(course.getId()).orElseThrow();
    assertThat(foundCourse.getTitle()).isEqualTo("JPA 입문");
    assertThat(foundCourse.getStatus()).isEqualTo(CourseStatus.OPEN);
    entityManager.clear();

    CourseSession foundSession = entityManager.find(CourseSession.class, session.getId());
    assertThat(foundSession.getStartAt()).isEqualTo(startAt);
    assertThat(foundSession.getStatus()).isEqualTo(CourseSessionStatus.OPEN);
    assertThat(Hibernate.isInitialized(foundSession.getCourse())).isFalse();
    assertThat(foundSession.getCourse().getId()).isEqualTo(course.getId());
  }
}
