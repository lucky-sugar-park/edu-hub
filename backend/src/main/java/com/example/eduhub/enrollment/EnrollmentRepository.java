package com.example.eduhub.enrollment;

import java.util.Collection;
import java.util.List;
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

  boolean existsBySessionIdAndEmployeeIdAndStatusIn(
      Long sessionId, String employeeId, Collection<EnrollmentStatus> statuses);
}
