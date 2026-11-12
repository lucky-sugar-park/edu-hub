package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;

/** 실제 빈의 트랜잭션 강제(MANDATORY)를 확인한다. 클래스에 {@code @Transactional}을 붙이지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
class VacancyFillerTransactionTest {

  @Autowired private VacancyFiller vacancyFiller;

  @Test
  @DisplayName("트랜잭션 밖에서 fillVacancies를 부르면 IllegalTransactionStateException이 난다")
  void fillVacanciesOutsideTransactionFails() {
    // 프록시가 메서드 실행 전에 트랜잭션 유무를 검사하므로 저장하지 않은 회차 객체로 충분하다.
    Instant now = Instant.now();
    CourseSession session =
        new CourseSession(
            new Course("트랜잭션 확인 과정", "테스트", null), now.plus(7, ChronoUnit.DAYS), "테스트실", 5);

    assertThatThrownBy(() -> vacancyFiller.fillVacancies(session, now))
        .isInstanceOf(IllegalTransactionStateException.class);
  }
}
