package com.example.demo.dto;

import java.time.LocalDate;

public record WorkingDaysResponse(LocalDate startDate, LocalDate endDate, int calendarDays, int workingDays) {
}
