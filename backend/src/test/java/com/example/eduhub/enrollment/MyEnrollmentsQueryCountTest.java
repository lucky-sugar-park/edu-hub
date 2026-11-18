package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.common.PageResponse;
import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseRepository;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 내 신청 내역 조회가 신청 수와 관계없이 쿼리 1~2개로 끝나는지 확인한다(NFR-02). */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class MyEnrollmentsQueryCountTest {

  private static final Instant START_AT = Instant.parse("2026-12-01T01:00:00Z");
  private static final Instant T0 = Instant.parse("2026-11-12T01:00:00Z");
  private static final String ME = "QC-ME";
  private static final String OTHER = "QC-OTHER";

  @Autowired private EnrollmentQueryService service;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private PlatformTransactionManager transactionManager;

  @PersistenceContext private EntityManager entityManager;

  private final List<Course> courses = new ArrayList<>();
  private final List<CourseSession> sessions = new ArrayList<>();
  private Long confirmedId;
  private Long waitingId2;
  private Long waitingId3;
  private Long cancelledId;

  @BeforeEach
  void setUp() {
    Course c1 = saveCourse("QC-과정1");
    Course c2 = saveCourse("QC-과정2");
    Course c3 = saveCourse("QC-과정3");
    CourseSession s1 = saveSession(c1, "QC-장소1");
    CourseSession s2 = saveSession(c2, "QC-장소2");
    CourseSession s3 = saveSession(c3, "QC-장소3");
    CourseSession s4 = saveSession(c3, "QC-장소4");

    confirmedId = enrollmentRepository.save(Enrollment.confirmed(s1, ME, T0)).getId();
    enrollmentRepository.save(Enrollment.waiting(s2, OTHER, T0, 1L));
    waitingId2 =
        enrollmentRepository.save(Enrollment.waiting(s2, ME, T0.plusSeconds(10), 2L)).getId();
    enrollmentRepository.save(Enrollment.waiting(s3, OTHER, T0, 1L));
    enrollmentRepository.save(Enrollment.waiting(s3, "QC-THIRD", T0, 2L));
    waitingId3 =
        enrollmentRepository.save(Enrollment.waiting(s3, ME, T0.plusSeconds(20), 3L)).getId();
    Enrollment cancelled =
        enrollmentRepository.save(Enrollment.confirmed(s4, ME, T0.plusSeconds(30)));
    cancelled.cancel(T0.plusSeconds(40));
    cancelledId = enrollmentRepository.save(cancelled).getId();
    enrollmentRepository.save(Enrollment.confirmed(s4, OTHER, T0));
  }

  @AfterEach
  void tearDown() {
    List<Long> sessionIds = sessions.stream().map(CourseSession::getId).toList();
    List<Long> courseIds = courses.stream().map(Course::getId).toList();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> {
          if (!sessionIds.isEmpty()) {
            entityManager
                .createQuery("delete from Enrollment e where e.session.id in :ids")
                .setParameter("ids", sessionIds)
                .executeUpdate();
            entityManager
                .createQuery("delete from CourseSession s where s.id in :ids")
                .setParameter("ids", sessionIds)
                .executeUpdate();
          }
          if (!courseIds.isEmpty()) {
            entityManager
                .createQuery("delete from Course c where c.id in :ids")
                .setParameter("ids", courseIds)
                .executeUpdate();
          }
        });
  }

  @Test
  void runsCountQueryWhenPageIsFull() {
    Statistics statistics = statistics();
    // 데이터는 repository 저장으로 이미 커밋됐고 서비스는 새 readOnly 트랜잭션과 영속성 컨텍스트에서
    // 실행되므로, 준비 쿼리와 1차 캐시가 측정에 섞이지 않는다.
    statistics.clear();

    PageResponse<MyEnrollmentResponse> response =
        service.getMyEnrollments(ME, null, PageRequest.of(0, 2));

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2L);
    assertThat(response.totalElements()).isEqualTo(4);
    assertThat(response.content())
        .extracting(MyEnrollmentResponse::id)
        .containsExactly(cancelledId, waitingId3);
  }

  @Test
  void listsAllStatusesWithinTwoStatements() {
    Statistics statistics = statistics();
    statistics.clear();

    PageResponse<MyEnrollmentResponse> response =
        service.getMyEnrollments(ME, null, PageRequest.of(0, 20));

    long statements = statistics.getPrepareStatementCount();
    assertThat(statements).isBetween(1L, 2L);
    assertThat(response.totalElements()).isEqualTo(4);
    assertThat(response.content())
        .extracting(MyEnrollmentResponse::id)
        .containsExactly(cancelledId, waitingId3, waitingId2, confirmedId);
    assertThat(response.content())
        .extracting(MyEnrollmentResponse::courseTitle)
        .containsExactly("QC-과정3", "QC-과정3", "QC-과정2", "QC-과정1");
    assertThat(response.content())
        .extracting(MyEnrollmentResponse::sessionPlace)
        .containsExactly("QC-장소4", "QC-장소3", "QC-장소2", "QC-장소1");
    assertThat(response.content())
        .extracting(MyEnrollmentResponse::waitingNumber)
        .containsExactly(null, 3, 2, null);
    assertThat(response.content())
        .extracting(MyEnrollmentResponse::status)
        .containsExactly(
            EnrollmentStatus.CANCELLED,
            EnrollmentStatus.WAITING,
            EnrollmentStatus.WAITING,
            EnrollmentStatus.CONFIRMED);
  }

  @Test
  void listsWaitingOnlyWithinTwoStatements() {
    Statistics statistics = statistics();
    statistics.clear();

    PageResponse<MyEnrollmentResponse> response =
        service.getMyEnrollments(ME, EnrollmentStatus.WAITING, PageRequest.of(0, 20));

    long statements = statistics.getPrepareStatementCount();
    assertThat(statements).isBetween(1L, 2L);
    assertThat(response.content())
        .extracting(MyEnrollmentResponse::id)
        .containsExactly(waitingId3, waitingId2);
    assertThat(response.content())
        .extracting(MyEnrollmentResponse::waitingNumber)
        .containsExactly(3, 2);
  }

  private Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  private Course saveCourse(String title) {
    Course course = courseRepository.save(new Course(title, "개발", "쿼리 수 테스트"));
    courses.add(course);
    return course;
  }

  private CourseSession saveSession(Course course, String place) {
    CourseSession session = sessionRepository.save(new CourseSession(course, START_AT, place, 10));
    sessions.add(session);
    return session;
  }
}
