package com.example.eduhub.enrollment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

  long countBySessionIdAndStatus(Long sessionId, EnrollmentStatus status);

  @Query(
      "select e.session.id as sessionId, count(e) as confirmed from Enrollment e "
          + "where e.session.id in :sessionIds and e.status = :status group by e.session.id")
  List<SessionConfirmedCount> countBySessionIdsAndStatus(
      @Param("sessionIds") Collection<Long> sessionIds, @Param("status") EnrollmentStatus status);

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

  Optional<Enrollment> findBySessionIdAndEmployeeIdAndStatusIn(
      Long sessionId, String employeeId, Collection<EnrollmentStatus> statuses);

  boolean existsBySessionIdAndEmployeeIdAndStatusIn(
      Long sessionId, String employeeId, Collection<EnrollmentStatus> statuses);
}
