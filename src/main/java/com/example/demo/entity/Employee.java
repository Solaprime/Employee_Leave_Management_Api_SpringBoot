package com.example.demo.entity;

import com.example.demo.model.Role;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "employees",
		// Last line of defence: the database itself refuses a negative balance,
		// even if a bug or a race slipped past the service-layer checks.
		check = @CheckConstraint(name = "ck_employees_balance_non_negative", constraint = "leave_balance >= 0"))
public class Employee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String username;

	@Column(nullable = false)
	private String password;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(name = "leave_balance", nullable = false)
	private int leaveBalance;

	@Version
	private Long version;

	protected Employee() {
	}

	public Employee(String username, String password, String fullName, Role role, int leaveBalance) {
		this.username = username;
		this.password = password;
		this.fullName = fullName;
		this.role = role;
		this.leaveBalance = leaveBalance;
	}

	public void deductLeave(int days) {
		if (days > leaveBalance) {
			throw new IllegalStateException("Insufficient leave balance");
		}
		leaveBalance -= days;
	}

	public boolean isManager() {
		return role == Role.MANAGER;
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getFullName() {
		return fullName;
	}

	public Role getRole() {
		return role;
	}

	public int getLeaveBalance() {
		return leaveBalance;
	}

	public Long getVersion() {
		return version;
	}
}
