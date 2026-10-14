package com.example.demo.config;

import com.example.demo.entity.Employee;
import com.example.demo.entity.LeaveRequest;
import com.example.demo.entity.PublicHoliday;
import com.example.demo.model.Role;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.LeaveRequestRepository;
import com.example.demo.repository.PublicHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prepopulates the database on startup (only when it is empty).
 * All users share the password {@code password123}.
 */
@Component
public class DataSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
	private static final String DEFAULT_PASSWORD = "password123";

	private final EmployeeRepository employeeRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final PublicHolidayRepository publicHolidayRepository;
	private final PasswordEncoder passwordEncoder;

	public DataSeeder(EmployeeRepository employeeRepository, LeaveRequestRepository leaveRequestRepository,
			PublicHolidayRepository publicHolidayRepository, PasswordEncoder passwordEncoder) {
		this.employeeRepository = employeeRepository;
		this.leaveRequestRepository = leaveRequestRepository;
		this.publicHolidayRepository = publicHolidayRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(String... args) {
		if (employeeRepository.count() > 0) {
			return;
		}
		String password = passwordEncoder.encode(DEFAULT_PASSWORD);

		Employee carol = new Employee("carol", password, "Carol Manager", Role.MANAGER, 25);
		Employee dave = new Employee("dave", password, "Dave Manager", Role.MANAGER, 25);
		// alice's balance already reflects the approved September leave below (20 - 3).
		Employee alice = new Employee("alice", password, "Alice Employee", Role.EMPLOYEE, 17);
		Employee bob = new Employee("bob", password, "Bob Employee", Role.EMPLOYEE, 15);
		employeeRepository.saveAll(List.of(carol, dave, alice, bob));

		publicHolidayRepository.saveAll(List.of(
				new PublicHoliday(LocalDate.of(2026, 10, 1), "Independence Day"),
				new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas Day"),
				new PublicHoliday(LocalDate.of(2026, 12, 28), "Boxing Day (observed)"),
				new PublicHoliday(LocalDate.of(2027, 1, 1), "New Year's Day")));

		LeaveRequest aliceSeptember = new LeaveRequest(alice,
				LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9), 3, "Short break");
		aliceSeptember.approve(carol, "Approved");

		// Friday to Tuesday: 3 working days (Logic 1).
		LeaveRequest aliceFriToTue = new LeaveRequest(alice,
				LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 27), 3, "Long weekend");

		// Two overlapping pending requests for bob, to try the concurrent-approval scenario (Logic 2).
		LeaveRequest bobA = new LeaveRequest(bob,
				LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 5, "Holiday - option A");
		LeaveRequest bobB = new LeaveRequest(bob,
				LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 10), 5, "Holiday - option B");

		LeaveRequest bobRejected = new LeaveRequest(bob,
				LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 31), 6, "End of year");
		bobRejected.reject(dave, "Year-end freeze");

		leaveRequestRepository.saveAll(List.of(aliceSeptember, aliceFriToTue, bobA, bobB, bobRejected));
		log.info("Seeded {} employees and {} leave requests", employeeRepository.count(), leaveRequestRepository.count());
	}
}
