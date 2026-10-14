package com.example.demo.service;

import com.example.demo.dto.EmployeeResponse;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.EmployeeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

	private final EmployeeRepository employeeRepository;

	public EmployeeService(EmployeeRepository employeeRepository) {
		this.employeeRepository = employeeRepository;
	}

	@Transactional(readOnly = true)
	public EmployeeResponse getByUsername(String username) {
		return employeeRepository.findByUsername(username)
				.map(EmployeeResponse::from)
				.orElseThrow(() -> new NotFoundException("Employee " + username + " not found"));
	}

	@Transactional(readOnly = true)
	public List<EmployeeResponse> findAll() {
		return employeeRepository.findAll().stream().map(EmployeeResponse::from).toList();
	}
}
