package com.kiwobollae.api.timelapse.exception;

public class TimelapseEncodingException extends RuntimeException {

	public TimelapseEncodingException(String message) {
		super(message);
	}

	public TimelapseEncodingException(String message, Throwable cause) {
		super(message, cause);
	}
}
