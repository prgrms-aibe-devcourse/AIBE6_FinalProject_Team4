package com.kiwobollae.api.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper. Business logic for constructing responses is left to the team.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

	private boolean success;
	private T data;
	private String message;

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static <T> ApiResponse<T> error(String message) {
		return new ApiResponse<>(false, null, message);
	}
}
