package com.example.eduhub.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

  Optional<Employee> findByEmployeeNo(String employeeNo);

  boolean existsByEmployeeNo(String employeeNo);
}
