package com.example.demo.controller;

import com.example.demo.dto.EmployeeResponse;
import com.example.demo.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees")
public class EmployeeController {

	private final EmployeeService employeeService;

	public EmployeeController(EmployeeService employeeService) {
		this.employeeService = employeeService;
	}

	@GetMapping("/me")
	@Operation(summary = "Profile and remaining leave balance of the authenticated user")
	public EmployeeResponse me(Principal principal) {
		return employeeService.getByUsername(principal.getName());
	}

	@GetMapping
	@PreAuthorize("hasRole('MANAGER')")
	@Operation(summary = "List all employees (managers)")
	public List<EmployeeResponse> list() {
		return employeeService.findAll();
	}
}
