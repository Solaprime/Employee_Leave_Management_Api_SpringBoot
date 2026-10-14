package com.example.demo.dto;

import com.example.demo.entity.Employee;
import com.example.demo.model.Role;

public record EmployeeResponse(Long id, String username, String fullName, Role role, int leaveBalance) {

	public static EmployeeResponse from(Employee e) {
		return new EmployeeResponse(e.getId(), e.getUsername(), e.getFullName(), e.getRole(), e.getLeaveBalance());
	}
}
