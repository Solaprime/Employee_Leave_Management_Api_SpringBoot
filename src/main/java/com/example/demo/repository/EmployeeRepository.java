package com.example.demo.repository;

import com.example.demo.entity.Employee;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

	Optional<Employee> findByUsername(String username);

	/**
	 * SELECT ... FOR UPDATE on the employee row. Every operation that changes an employee's
	 * leave (create, approve, reject, cancel) takes this lock first, so they are serialised per employee.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
	@Query("select e from Employee e where e.id = :id")
	Optional<Employee> findByIdForUpdate(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
	@Query("select e from Employee e where e.username = :username")
	Optional<Employee> findByUsernameForUpdate(@Param("username") String username);
}
