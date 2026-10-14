package com.example.demo.repository;

import com.example.demo.entity.PublicHoliday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

	List<PublicHoliday> findByDateBetween(LocalDate start, LocalDate end);
}
