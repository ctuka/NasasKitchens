package com.nanaskitchens.api.auth.dto;

public record TokenPairResponse(String accessToken, String refreshToken) {
}
