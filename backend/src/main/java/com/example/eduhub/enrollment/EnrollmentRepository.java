package com.example.eduhub.enrollment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

  long countBySessionIdAndStatus(Long sessionId, EnrollmentStatus status);

  @Query(
      "select e.session.id as sessionId, count(e) as confirmed from Enrollment e "
          + "where e.session.id in :sessionIds and e.status in :statuses group by e.session.id")
  List<SessionConfirmedCount> countBySessionIdsAndStatusIn(
      @Param("sessionIds") Collection<Long> sessionIds,
      @Param("statuses") Collection<EnrollmentStatus> statuses);

  @Query(
      "select max(e.waitingKey) from Enrollment e "
          + "where e.session.id = :sessionId and e.status = 'WAITING'")
  Optional<Long> findMaxWaitingKey(@Param("sessionId") Long sessionId);

  @Query(
      "select min(e.waitingKey) from Enrollment e "
          + "where e.session.id = :sessionId and e.status = 'WAITING'")
  Optional<Long> findMinWaitingKey(@Param("sessionId") Long sessionId);

  long countBySessionIdAndStatusAndWaitingKeyLessThan(
      Long sessionId, EnrollmentStatus status, Long waitingKey);

  List<Enrollment> findBySessionIdAndStatusOrderByConfirmedAtDescIdDesc(
      Long sessionId, EnrollmentStatus status, Pageable pageable);

  List<Enrollment> findBySessionIdAndStatusOrderByWaitingKeyAsc(
      Long sessionId, EnrollmentStatus status, Pageable pageable);

  /** 회차의 신청을 상태별로 한 번에 읽는다. session 연관은 접근하지 않으므로 지연 로딩이 없다. */
  List<Enrollment> findBySessionIdAndStatusIn(
      Long sessionId, Collection<EnrollmentStatus> statuses);

  /** 수료 처리용. 사번 여러 명의 신청(상태 무관)을 한 번에 읽는다. */
  List<Enrollment> findBySessionIdAndEmployeeIdIn(Long sessionId, Collection<String> employeeIds);

  Optional<Enrollment> findBySessionIdAndEmployeeIdAndStatusIn(
      Long sessionId, String employeeId, Collection<EnrollmentStatus> statuses);

  boolean existsBySessionIdAndEmployeeIdAndStatusIn(
      Long sessionId, String employeeId, Collection<EnrollmentStatus> statuses);

  /** 내 신청 내역. status가 null이면 전체. 대기 순번은 D-23 식을 서브쿼리로 계산한다. */
  @Query(
      value =
          "select new com.example.eduhub.enrollment.MyEnrollmentRow("
              + "e.id, c.title, s.id, s.startAt, s.place, e.status, "
              + "case when e.status = 'WAITING' then "
              + "(select count(w) + 1 from Enrollment w "
              + "where w.session = s and w.status = 'WAITING' and w.waitingKey < e.waitingKey) "
              + "end, e.appliedAt) "
              + "from Enrollment e join e.session s join s.course c "
              + "where e.employeeId = :employeeId and (:status is null or e.status = :status) "
              + "order by e.appliedAt desc, e.id desc",
      countQuery =
          "select count(e) from Enrollment e "
              + "where e.employeeId = :employeeId and (:status is null or e.status = :status)")
  Page<MyEnrollmentRow> findMyEnrollments(
      @Param("employeeId") String employeeId,
      @Param("status") EnrollmentStatus status,
      Pageable pageable);
}
