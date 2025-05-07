package io.mosip.registration.processor.core.exception;

import io.mosip.kernel.core.exception.BaseCheckedException;

public class PacketManagerFailureException extends BaseCheckedException {

    public PacketManagerFailureException(String errorCode, String message) {
        super(errorCode, message);
    }

    public PacketManagerFailureException(String errorCode, String message, Throwable t) {
        super(errorCode, message, t);
    }
}
