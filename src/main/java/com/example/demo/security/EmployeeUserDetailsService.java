package com.example.demo.security;

import com.example.demo.repository.EmployeeRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Authenticates against the employees table; the employee's Role becomes a ROLE_ authority. */
@Service
public class EmployeeUserDetailsService implements UserDetailsService {

	private final EmployeeRepository employeeRepository;

	public EmployeeUserDetailsService(EmployeeRepository employeeRepository) {
		this.employeeRepository = employeeRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		return employeeRepository.findByUsername(username)
				.map(e -> User.withUsername(e.getUsername())
						.password(e.getPassword())
						.roles(e.getRole().name())
						.build())
				.orElseThrow(() -> new UsernameNotFoundException("Unknown user " + username));
	}
}
