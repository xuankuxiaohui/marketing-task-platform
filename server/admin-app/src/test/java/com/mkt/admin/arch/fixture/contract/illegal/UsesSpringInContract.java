package com.mkt.admin.arch.fixture.contract.illegal;

import org.springframework.stereotype.Service;

/** Deliberate RL-06 violation: contract-shaped type depends on Spring. */
@Service
public final class UsesSpringInContract {
}
