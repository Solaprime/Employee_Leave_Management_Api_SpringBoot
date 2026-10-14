package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "public_holidays")
public class PublicHoliday {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "holiday_date", nullable = false, unique = true)
	private LocalDate date;

	@Column(nullable = false, length = 100)
	private String name;

	protected PublicHoliday() {
	}

	public PublicHoliday(LocalDate date, String name) {
		this.date = date;
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public LocalDate getDate() {
		return date;
	}

	public String getName() {
		return name;
	}
}
