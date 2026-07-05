package com.nanaskitchens.api.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ChatRequest(@NotEmpty @Valid List<ChatMessage> messages) {
}
