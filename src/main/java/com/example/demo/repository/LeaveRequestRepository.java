package com.example.demo.repository;

import com.example.demo.entity.LeaveRequest;
import com.example.demo.model.LeaveStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

	/** Looks up the owner without loading the request entity, so the request can then be loaded under a lock. */
	@Query("select r.employee.id from LeaveRequest r where r.id = :id")
	Optional<Long> findEmployeeIdById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
	@Query("select r from LeaveRequest r where r.id = :id")
	Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

	@EntityGraph(attributePaths = {"employee", "decidedBy"})
	@Query("select r from LeaveRequest r where r.id = :id")
	Optional<LeaveRequest> findWithDetailsById(@Param("id") Long id);

	@EntityGraph(attributePaths = {"employee", "decidedBy"})
	@Query("""
			select r from LeaveRequest r
			where (:employeeId is null or r.employee.id = :employeeId)
			  and (:status is null or r.status = :status)
			""")
	Page<LeaveRequest> search(@Param("employeeId") Long employeeId,
			@Param("status") LeaveStatus status,
			Pageable pageable);

	/** Inclusive date ranges [a,b] and [c,d] overlap when a <= d and c <= b. */
	@Query("""
			select count(r) > 0 from LeaveRequest r
			where r.employee.id = :employeeId
			  and r.status = :status
			  and (:excludeId is null or r.id <> :excludeId)
			  and r.startDate <= :endDate
			  and r.endDate >= :startDate
			""")
	boolean existsOverlapping(@Param("employeeId") Long employeeId,
			@Param("status") LeaveStatus status,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate,
			@Param("excludeId") Long excludeId);
}
