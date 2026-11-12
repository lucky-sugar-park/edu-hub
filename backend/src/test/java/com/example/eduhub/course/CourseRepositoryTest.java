package com.example.eduhub.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CourseRepositoryTest {

  private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "id");
  private static final Pageable FIRST_PAGE = PageRequest.of(0, 10, LATEST_FIRST);

  @Autowired private CourseRepository courseRepository;

  @Autowired private CourseSessionRepository courseSessionRepository;

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

  @Test
  void searchWithoutConditionsReturnsAll() {
    saveCourse("JPA 입문", "개발");
    saveCourse("리더십", "경영");

    assertThat(titlesOf(courseRepository.search(null, null, FIRST_PAGE)))
        .containsExactly("리더십", "JPA 입문");
    assertThat(titlesOf(courseRepository.search(" ", "", FIRST_PAGE)))
        .containsExactly("리더십", "JPA 입문");
  }

  @Test
  void searchMatchesPartOfTitle() {
    saveCourse("Spring JPA 입문", "개발");
    saveCourse("리더십", "경영");

    assertThat(titlesOf(courseRepository.search("JPA", null, FIRST_PAGE)))
        .containsExactly("Spring JPA 입문");
  }

  @Test
  void searchIgnoresKeywordCase() {
    saveCourse("Spring JPA 입문", "개발");

    assertThat(titlesOf(courseRepository.search("jpa", null, FIRST_PAGE)))
        .containsExactly("Spring JPA 입문");
  }

  @Test
  void searchMatchesExactCategory() {
    saveCourse("JPA 입문", "개발");
    saveCourse("리더십", "경영");
    saveCourse("개발 문화", "개발문화");

    assertThat(titlesOf(courseRepository.search(null, "개발", FIRST_PAGE))).containsExactly("JPA 입문");
  }

  @Test
  void searchCombinesKeywordAndCategory() {
    saveCourse("JPA 입문", "개발");
    saveCourse("JPA 사례 공유", "경영");
    saveCourse("Kotlin 입문", "개발");

    assertThat(titlesOf(courseRepository.search("JPA", "개발", FIRST_PAGE)))
        .containsExactly("JPA 입문");
  }

  @Test
  void searchWithoutMatchReturnsEmptyPage() {
    saveCourse("JPA 입문", "개발");

    Page<Course> page = courseRepository.search("없는 제목", null, FIRST_PAGE);

    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isZero();
  }

  @Test
  void searchReturnsRequestedPageAndTotal() {
    saveCourse("과정 1", "개발");
    saveCourse("과정 2", "개발");
    saveCourse("과정 3", "개발");

    Page<Course> page = courseRepository.search(null, null, PageRequest.of(1, 2, LATEST_FIRST));

    assertThat(titlesOf(page)).containsExactly("과정 1");
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void searchTreatsWildcardsAsLiterals() {
    saveCourse("100% 실습", "개발");
    saveCourse("1000 실습", "개발");
    saveCourse("snake_case", "개발");
    saveCourse("snakeXcase", "개발");
    saveCourse("느낌표! 과정", "개발");

    assertThat(titlesOf(courseRepository.search("100%", null, FIRST_PAGE)))
        .containsExactly("100% 실습");
    assertThat(titlesOf(courseRepository.search("snake_case", null, FIRST_PAGE)))
        .containsExactly("snake_case");
    assertThat(titlesOf(courseRepository.search("표!", null, FIRST_PAGE)))
        .containsExactly("느낌표! 과정");
  }

  @Test
  void findsSessionsOfCourseInStartOrder() {
    Course course = saveCourse("JPA 입문", "개발");
    Course other = saveCourse("리더십", "경영");
    Instant earlier = Instant.parse("2026-12-01T01:00:00Z");
    Instant later = Instant.parse("2026-12-08T01:00:00Z");
    entityManager.persist(new CourseSession(course, later, "본사 3층", 20));
    entityManager.persist(new CourseSession(course, earlier, "본사 2층", 10));
    entityManager.persist(new CourseSession(other, earlier, "본사 1층", 30));
    entityManager.flush();
    entityManager.clear();

    List<CourseSession> sessions =
        courseSessionRepository.findByCourseIdOrderByStartAtAscIdAsc(course.getId());

    assertThat(sessions).extracting(CourseSession::getStartAt).containsExactly(earlier, later);
  }

  @Test
  void persistsCourseAndSessionChanges() {
    Instant now = Instant.parse("2026-11-12T00:00:00Z");
    Instant startAt = Instant.parse("2026-12-01T01:00:00Z");
    Course course = saveCourse("JPA 입문", "개발");
    CourseSession session =
        entityManager.persistAndFlush(new CourseSession(course, startAt, "본사 3층", 20));

    course.update("JPA 심화", "개발", "심화 과정");
    course.close();
    session.update(startAt.plusSeconds(3600), "별관", 30, now);
    entityManager.flush();
    entityManager.clear();

    Course foundCourse = courseRepository.findById(course.getId()).orElseThrow();
    assertThat(foundCourse.getTitle()).isEqualTo("JPA 심화");
    assertThat(foundCourse.getDescription()).isEqualTo("심화 과정");
    assertThat(foundCourse.getStatus()).isEqualTo(CourseStatus.CLOSED);
    CourseSession foundSession = courseSessionRepository.findById(session.getId()).orElseThrow();
    assertThat(foundSession.getStartAt()).isEqualTo(startAt.plusSeconds(3600));
    assertThat(foundSession.getPlace()).isEqualTo("별관");
    assertThat(foundSession.getCapacity()).isEqualTo(30);
    assertThat(foundSession.getStatus()).isEqualTo(CourseSessionStatus.OPEN);

    foundSession.close(startAt.plusSeconds(7200));
    entityManager.flush();
    entityManager.clear();

    assertThat(courseSessionRepository.findById(session.getId()).orElseThrow().getStatus())
        .isEqualTo(CourseSessionStatus.CLOSED);
  }

  @Test
  void findsSessionForUpdateById() {
    Course course = saveCourse("JPA 입문", "개발");
    CourseSession session =
        entityManager.persistAndFlush(
            new CourseSession(course, Instant.parse("2026-12-01T01:00:00Z"), "본사 3층", 20));
    entityManager.clear();

    assertThat(courseSessionRepository.findByIdForUpdate(session.getId()))
        .get()
        .extracting(CourseSession::getId)
        .isEqualTo(session.getId());
    assertThat(courseSessionRepository.findByIdForUpdate(session.getId() + 1000)).isEmpty();
  }

  private Course saveCourse(String title, String category) {
    return courseRepository.saveAndFlush(new Course(title, category, null));
  }

  private List<String> titlesOf(Page<Course> page) {
    return page.map(Course::getTitle).getContent();
  }
}
