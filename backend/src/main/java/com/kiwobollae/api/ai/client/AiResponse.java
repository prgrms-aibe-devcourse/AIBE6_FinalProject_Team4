package com.kiwobollae.api.ai.client;

import tools.jackson.databind.JsonNode;

public record AiResponse(String responseId, String model, JsonNode result) {}
