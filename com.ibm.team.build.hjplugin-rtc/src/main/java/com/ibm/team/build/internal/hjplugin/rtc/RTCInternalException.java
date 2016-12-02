package com.ibm.team.build.internal.hjplugin.rtc;

import com.ibm.team.repository.common.InternalRepositoryException;

/**
 * A RTCInternalException is a wrapper over a {@link InternalRepositoryException}
 */
public class RTCInternalException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public RTCInternalException(String message, Throwable cause) {
		super(message, cause);
	}
}
