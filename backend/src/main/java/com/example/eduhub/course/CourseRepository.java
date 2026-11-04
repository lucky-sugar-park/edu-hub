package com.example.eduhub.course;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.StringUtils;

public interface CourseRepository extends JpaRepository<Course, Long> {

  default Page<Course> search(String keyword, String category, Pageable pageable) {
    return findByTitlePatternAndCategory(
        toLikePattern(keyword), StringUtils.hasText(category) ? category : null, pageable);
  }

  @Query(
      """
      select c from Course c
      where (:titlePattern is null or c.title like :titlePattern escape '!')
        and (:category is null or c.category = :category)
      """)
  Page<Course> findByTitlePatternAndCategory(
      @Param("titlePattern") String titlePattern,
      @Param("category") String category,
      Pageable pageable);

  private static String toLikePattern(String keyword) {
    if (!StringUtils.hasText(keyword)) {
      return null;
    }
    return "%" + keyword.replaceAll("([!%_])", "!$1") + "%";
  }
}
