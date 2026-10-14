package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkingDayCalculatorTest {

	private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 16);
	private static final LocalDate NEXT_TUESDAY = LocalDate.of(2026, 10, 20);

	@Test
	void fridayToFollowingTuesdayIsThreeWorkingDays() {
		assertThat(FRIDAY.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
		assertThat(NEXT_TUESDAY.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);

		assertThat(WorkingDayCalculator.countWorkingDays(FRIDAY, NEXT_TUESDAY, Set.of())).isEqualTo(3);
	}

	@Test
	void publicHolidayInsideRangeIsNotCharged() {
		LocalDate monday = LocalDate.of(2026, 10, 19);
		assertThat(WorkingDayCalculator.countWorkingDays(FRIDAY, NEXT_TUESDAY, Set.of(monday))).isEqualTo(2);
	}

	@Test
	void weekendOnlyRangeIsZero() {
		assertThat(WorkingDayCalculator.countWorkingDays(
				LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 18), Set.of())).isZero();
	}

	@Test
	void singleWeekdayIsOne() {
		assertThat(WorkingDayCalculator.countWorkingDays(FRIDAY, FRIDAY, Set.of())).isEqualTo(1);
	}

	@Test
	void fullWeekIsFive() {
		assertThat(WorkingDayCalculator.countWorkingDays(
				LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), Set.of())).isEqualTo(5);
	}

	@Test
	void endBeforeStartIsRejected() {
		assertThatThrownBy(() -> WorkingDayCalculator.countWorkingDays(NEXT_TUESDAY, FRIDAY, Set.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
