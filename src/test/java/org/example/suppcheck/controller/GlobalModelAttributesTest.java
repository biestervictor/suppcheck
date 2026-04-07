package org.example.suppcheck.controller;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GlobalModelAttributesTest {

    @Test
    void appVersion_returnsInjectedValue() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "appVersion", "1.2.3");

        assertEquals("1.2.3", advice.appVersion());
    }

    @Test
    void buildTimestamp_returnsInjectedValue() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "buildTimestamp", "2026-04-07T13:00:00Z");

        assertEquals("2026-04-07T13:00:00Z", advice.buildTimestamp());
    }

    @Test
    void defaultValues_returnDash() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "appVersion", "-");
        ReflectionTestUtils.setField(advice, "buildTimestamp", "-");

        assertEquals("-", advice.appVersion());
        assertEquals("-", advice.buildTimestamp());
    }
}

