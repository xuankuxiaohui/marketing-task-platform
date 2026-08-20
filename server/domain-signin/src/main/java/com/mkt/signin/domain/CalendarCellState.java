package com.mkt.signin.domain;

/** R36.1 four calendar states plus none for grey cells. */
public enum CalendarCellState {
    SIGNED,
    CATCHUP,
    MISSED_CATCHABLE,
    TODAY_AVAILABLE,
    NONE
}
