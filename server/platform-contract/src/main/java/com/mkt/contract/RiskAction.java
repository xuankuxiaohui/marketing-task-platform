package com.mkt.contract;

/** Verdict action (design §2.2.3). Freeze is not expressed here. */
public enum RiskAction {
    PASS,
    REJECT,
    SILENT_REJECT,
    MARK
}
