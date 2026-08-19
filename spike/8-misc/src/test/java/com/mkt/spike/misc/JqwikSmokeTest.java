package com.mkt.spike.misc;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JqwikSmokeTest {

    @Property
    void additionIsCommutative(@ForAll int a, @ForAll int b) {
        assertEquals(a + b, b + a);
    }

    @Test
    void junitStillRunsBesideJqwik() {
        assertEquals(2, 1 + 1);
    }
}
