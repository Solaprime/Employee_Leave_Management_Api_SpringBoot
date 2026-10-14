package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.DecisionResult;
import com.example.demo.entity.Employee;
import com.example.demo.entity.LeaveRequest;
import com.example.demo.exception.ConflictException;
import com.example.demo.model.LeaveStatus;
import com.example.demo.model.Role;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.LeaveRequestRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Real transactions on separate threads; deliberately not @Transactional. */
@SpringBootTest
class LeaveApprovalConcurrencyTest {

	@Autowired
	LeaveRequestService service;
	@Autowired
	EmployeeRepository employeeRepository;
	@Autowired
	LeaveRequestRepository leaveRequestRepository;

	Employee employee;
	Employee managerOne;
	Employee managerTwo;

	@BeforeEach
	void setUp() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		employee = employeeRepository.save(new Employee("emp-" + suffix, "x", "Employee", Role.EMPLOYEE, 20));
		managerOne = employeeRepository.save(new Employee("m1-" + suffix, "x", "Manager One", Role.MANAGER, 20));
		managerTwo = employeeRepository.save(new Employee("m2-" + suffix, "x", "Manager Two", Role.MANAGER, 20));
	}

	@Test
	void twoManagersApprovingOverlappingRequestsAtOnce_onlyOneIsApproved() throws Exception {
		LeaveRequest a = pending(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 5), 5);
		LeaveRequest b = pending(LocalDate.of(2027, 3, 4), LocalDate.of(2027, 3, 10), 5);

		List<Outcome> outcomes = runConcurrently(
				() -> service.approve(a.getId(), managerOne.getUsername(), null),
				() -> service.approve(b.getId(), managerTwo.getUsername(), null));

		assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
		assertThat(outcomes).filteredOn(o -> o.error() instanceof ConflictException).hasSize(1);

		long approved = leaveRequestRepository.findAllById(List.of(a.getId(), b.getId())).stream()
				.filter(r -> r.getStatus() == LeaveStatus.APPROVED).count();
		assertThat(approved).isEqualTo(1);
		assertThat(balance()).isEqualTo(15);
	}

	@Test
	void doubleClickApprove_isIdempotentAndDeductsOnce() throws Exception {
		LeaveRequest r = pending(LocalDate.of(2027, 4, 2), LocalDate.of(2027, 4, 6), 3);

		List<Outcome> outcomes = runConcurrently(
				() -> service.approve(r.getId(), managerOne.getUsername(), null),
				() -> service.approve(r.getId(), managerOne.getUsername(), null));

		assertThat(outcomes).allMatch(Outcome::succeeded);
		assertThat(outcomes).extracting(o -> o.result().replayed()).containsExactlyInAnyOrder(false, true);
		assertThat(outcomes).allMatch(o -> o.result().leaveRequest().status() == LeaveStatus.APPROVED);
		assertThat(balance()).isEqualTo(17);
	}

	@Test
	void sequentialRepeatApprove_returnsSameStateWithoutSecondDeduction() {
		LeaveRequest r = pending(LocalDate.of(2027, 5, 3), LocalDate.of(2027, 5, 4), 2);

		DecisionResult first = service.approve(r.getId(), managerOne.getUsername(), "ok");
		DecisionResult second = service.approve(r.getId(), managerTwo.getUsername(), "ok again");

		assertThat(first.replayed()).isFalse();
		assertThat(second.replayed()).isTrue();
		assertThat(second.leaveRequest().decidedById()).isEqualTo(managerOne.getId());
		assertThat(second.leaveRequest().decidedAt()).isEqualTo(first.leaveRequest().decidedAt());
		assertThat(balance()).isEqualTo(18);
	}

	@Test
	void approvingARejectedRequest_isAConflict() {
		LeaveRequest r = pending(LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 1), 1);
		service.reject(r.getId(), managerOne.getUsername(), "no");

		assertThatThrownBy(() -> service.approve(r.getId(), managerTwo.getUsername(), null))
				.isInstanceOf(ConflictException.class);
		assertThat(balance()).isEqualTo(20);
	}

	private LeaveRequest pending(LocalDate start, LocalDate end, int days) {
		return leaveRequestRepository.save(new LeaveRequest(employee, start, end, days, "test"));
	}

	private int balance() {
		return employeeRepository.findById(employee.getId()).orElseThrow().getLeaveBalance();
	}

	@SafeVarargs
	private static List<Outcome> runConcurrently(Callable<DecisionResult>... tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Outcome>> futures = new ArrayList<>();
			for (Callable<DecisionResult> task : tasks) {
				futures.add(pool.submit(() -> {
					start.await();
					try {
						return new Outcome(task.call(), null);
					}
					catch (Exception e) {
						return new Outcome(null, e);
					}
				}));
			}
			start.countDown();
			List<Outcome> outcomes = new ArrayList<>();
			for (Future<Outcome> f : futures) {
				outcomes.add(f.get());
			}
			return outcomes;
		}
		finally {
			pool.shutdownNow();
		}
	}

	record Outcome(DecisionResult result, Exception error) {
		boolean succeeded() {
			return error == null;
		}
	}
}
