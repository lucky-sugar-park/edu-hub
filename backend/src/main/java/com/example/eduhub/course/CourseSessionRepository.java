package com.example.eduhub.course;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CourseSessionRepository extends JpaRepository<CourseSession, Long> {

  List<CourseSession> findByCourseIdOrderByStartAtAscIdAsc(Long courseId);

  /**
   * 회차 행을 비관적 쓰기 락으로 조회한다. 신청 트랜잭션의 첫 쿼리로 쓴다. 과정 행까지 잠기지 않도록 fetch join을 붙이지 않는다(D-06). 락 대기는
   * 3초까지다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("select s from CourseSession s where s.id = :id")
  Optional<CourseSession> findByIdForUpdate(@Param("id") Long id);
}
