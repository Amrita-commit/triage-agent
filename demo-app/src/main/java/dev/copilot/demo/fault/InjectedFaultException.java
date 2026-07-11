package dev.copilot.demo.fault;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when an injected error-rate fault trips, producing an HTTP 500 in the demo app. */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class InjectedFaultException extends RuntimeException {
    public InjectedFaultException(String message) {
        super(message);
    }
}
