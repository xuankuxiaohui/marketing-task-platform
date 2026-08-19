package com.mkt.kernel;

/** Domain error contract. Domain enums implement this (docs/standards/09). */
public interface ErrorCode {

    String code();

    int httpStatus();

    String message();
}
