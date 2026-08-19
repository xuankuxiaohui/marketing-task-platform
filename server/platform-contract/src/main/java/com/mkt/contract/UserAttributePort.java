package com.mkt.contract;

/** Identity-owned user attributes (design §2.2.3 / D-12). */
public interface UserAttributePort {

    UserAttributes attributes(long userId);

    UserAttributes lockAndGet(long userId);
}
