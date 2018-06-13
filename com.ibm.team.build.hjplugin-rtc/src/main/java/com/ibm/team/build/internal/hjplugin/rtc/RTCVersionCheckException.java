package com.ibm.team.build.internal.hjplugin.rtc;

/**
 * Exception thrown when checking for RTC client or server versions.
 * It wraps other exceptions that occur with a context sensitive message
 */
public class RTCVersionCheckException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public RTCVersionCheckException(String message, Throwable cause) {
		super(message, cause);
	}
}
