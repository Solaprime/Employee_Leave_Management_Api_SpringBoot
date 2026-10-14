package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record DecisionRequest(@Schema(example = "Enjoy your break") @Size(max = 500) String comment) {
}
