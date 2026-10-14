package com.example.demo.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LeaveRequestControllerTest {

	private static final String PASSWORD = "password123";

	@Autowired
	MockMvc mvc;

	@Test
	void unauthenticatedCallsAreRejected() throws Exception {
		mvc.perform(get("/api/leave-requests")).andExpect(status().isUnauthorized());
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		mvc.perform(get("/api/leave-requests").with(httpBasic("alice", "nope")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void swaggerDocsArePublic() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void employeeCannotApprove() throws Exception {
		mvc.perform(post("/api/leave-requests/1/approve").with(httpBasic("alice", PASSWORD)))
				.andExpect(status().isForbidden());
	}

	@Test
	void employeeOnlySeesOwnRequests() throws Exception {
		mvc.perform(get("/api/leave-requests").with(httpBasic("alice", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].employeeName", everyItem(is("Alice Employee"))));
	}

	@Test
	void workingDaysPreview_fridayToTuesdayIsThree() throws Exception {
		mvc.perform(get("/api/leave-requests/working-days")
						.param("startDate", "2026-10-16").param("endDate", "2026-10-20")
						.with(httpBasic("bob", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.calendarDays").value(5))
				.andExpect(jsonPath("$.workingDays").value(3));
	}

	@Test
	void invalidDateRangeIsBadRequest() throws Exception {
		mvc.perform(post("/api/leave-requests").with(httpBasic("bob", PASSWORD))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"startDate\":\"2030-01-10\",\"endDate\":\"2030-01-01\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createThenApproveTwice_secondCallIsAnIdempotentReplay() throws Exception {
		LocalDate friday = LocalDate.now().plusYears(2).with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		LocalDate tuesday = friday.plusDays(4);

		String created = mvc.perform(post("/api/leave-requests").with(httpBasic("bob", PASSWORD))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"trip\"}".formatted(friday, tuesday)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.workingDays").value(3))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		Integer id = JsonPath.read(created, "$.id");

		mvc.perform(post("/api/leave-requests/{id}/approve", id).with(httpBasic("carol", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(header().string(LeaveRequestController.REPLAYED_HEADER, "false"))
				.andExpect(jsonPath("$.status").value("APPROVED"));

		mvc.perform(post("/api/leave-requests/{id}/approve", id).with(httpBasic("carol", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(header().string(LeaveRequestController.REPLAYED_HEADER, "true"))
				.andExpect(jsonPath("$.status").value("APPROVED"));

		mvc.perform(post("/api/leave-requests/{id}/reject", id).with(httpBasic("dave", PASSWORD)))
				.andExpect(status().isConflict());

		mvc.perform(get("/api/leave-requests/{id}", id).with(httpBasic("alice", PASSWORD)))
				.andExpect(status().isForbidden());
	}
}
