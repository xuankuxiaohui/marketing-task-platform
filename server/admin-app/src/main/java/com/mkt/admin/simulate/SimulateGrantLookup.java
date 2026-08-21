package com.mkt.admin.simulate;

import java.util.List;

/** Simulated grant / ledger lookup for reverse (admin-app JDBC; no domain mapper import). */
public interface SimulateGrantLookup {

    List<SimulateGrantRow> listByStepSourceIds(List<String> sourceIds);

    boolean reverseLogExists(long grantRecordId);

    Long earnAmount(long userId, String bizSource, String bizId);

    boolean reversalExists(String bizId);
}
