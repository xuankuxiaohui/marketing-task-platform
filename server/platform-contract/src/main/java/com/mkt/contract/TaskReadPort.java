package com.mkt.contract;

/** Read-only task facade. No write methods (D-13). */
public interface TaskReadPort {

    InstanceCounts instanceCounts(long userId);
}
