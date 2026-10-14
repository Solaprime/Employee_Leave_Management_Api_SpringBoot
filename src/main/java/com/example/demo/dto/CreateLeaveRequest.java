package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateLeaveRequest(
		@Schema(example = "2026-10-30") @NotNull @FutureOrPresent LocalDate startDate,
		@Schema(example = "2026-11-03") @NotNull LocalDate endDate,
		@Schema(example = "Family visit") @Size(max = 500) String reason) {

	@Schema(hidden = true)
	@AssertTrue(message = "endDate must be on or after startDate")
	public boolean isDateRangeValid() {
		return startDate == null || endDate == null || !endDate.isBefore(startDate);
	}
}
