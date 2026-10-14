package com.example.demo.service;

import com.example.demo.entity.PublicHoliday;
import com.example.demo.repository.PublicHolidayRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Logic 1 - date calculation.
 * <p>
 * Leave is charged per <em>working</em> day: both ends of the range are inclusive, Saturdays and
 * Sundays are free, and so are configured public holidays. Example: Friday to the following Tuesday
 * spans 5 calendar days (Fri, Sat, Sun, Mon, Tue) but only Fri, Mon and Tue are charged = 3 days.
 */
@Component
public class WorkingDayCalculator {

	private final PublicHolidayRepository publicHolidayRepository;

	public WorkingDayCalculator(PublicHolidayRepository publicHolidayRepository) {
		this.publicHolidayRepository = publicHolidayRepository;
	}

	public int countWorkingDays(LocalDate start, LocalDate end) {
		Set<LocalDate> holidays = publicHolidayRepository.findByDateBetween(start, end).stream()
				.map(PublicHoliday::getDate)
				.collect(Collectors.toSet());
		return countWorkingDays(start, end, holidays);
	}

	static int countWorkingDays(LocalDate start, LocalDate end, Set<LocalDate> holidays) {
		if (end.isBefore(start)) {
			throw new IllegalArgumentException("end date must not be before start date");
		}
		return (int) start.datesUntil(end.plusDays(1))
				.filter(d -> !isWeekend(d))
				.filter(d -> !holidays.contains(d))
				.count();
	}

	private static boolean isWeekend(LocalDate date) {
		DayOfWeek day = date.getDayOfWeek();
		return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
	}
}
