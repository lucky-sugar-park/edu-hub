package com.example.eduhub.enrollment;

import com.example.eduhub.course.CourseSession;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 빈자리를 대기자로 채운다(FR-06). */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class VacancyFiller {

  private final EnrollmentRepository enrollmentRepository;

  VacancyFiller(EnrollmentRepository enrollmentRepository) {
    this.enrollmentRepository = enrollmentRepository;
  }

  /**
   * 빈자리(정원 - 확정 수)만큼 대기자를 키 오름차순으로 확정 승급시킨다. 교육이 시작했으면 아무것도 하지 않는다(D-09).
   *
   * <p>트랜잭션이 필수이다(MANDATORY). 트랜잭션 밖에서 부르면 예외가 난다. 회차 락을 잡는 것은 호출자의 책임이며, 락을 잡은 트랜잭션 안에서 호출해야 한다.
   *
   * @return 승급시킨 수
   */
  int fillVacancies(CourseSession session, Instant now) {
    if (session.hasStarted(now)) {
      return 0;
    }
    Long sessionId = session.getId();
    long confirmed =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED);
    long vacancies = session.getCapacity() - confirmed;
    if (vacancies <= 0) {
      return 0;
    }
    List<Enrollment> promoted =
        enrollmentRepository.findBySessionIdAndStatusOrderByWaitingKeyAsc(
            sessionId, EnrollmentStatus.WAITING, PageRequest.of(0, Math.toIntExact(vacancies)));
    if (promoted.isEmpty()) {
      return 0;
    }
    promoted.forEach(enrollment -> enrollment.promote(now));
    enrollmentRepository.saveAllAndFlush(promoted);
    return promoted.size();
  }
}
