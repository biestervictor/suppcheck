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

    @Test
    void isProd_falseForDevHost() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://mongodb-service.treasury.svc.cluster.local:27017/suppcheck");

        assertFalse(advice.isProd());
    }

    @Test
    void isProd_trueForProdHost() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://192.168.178.141:27017/suppcheck");

        assertTrue(advice.isProd());
    }

    @Test
    void isProd_trueForProdHostWithCredentials() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://user:pass@prod-db.example.com:27017/suppcheck");

        assertTrue(advice.isProd());
    }

    @Test
    void isProd_falseForEmptyUri() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri", "");

        assertFalse(advice.isProd());
    }

    @Test
    void mongoHost_extractsHostFromSimpleUri() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://192.168.178.141:27017/suppcheck");

        assertEquals("192.168.178.141:27017", advice.mongoHost());
    }

    @Test
    void mongoHost_extractsHostFromUriWithCredentials() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://user:pass@myhost.example.com:27017/db?authSource=admin");

        assertEquals("myhost.example.com:27017", advice.mongoHost());
    }

    @Test
    void mongoHost_returnsEmptyForBlankUri() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri", "");

        assertEquals("", advice.mongoHost());
    }

    @Test
    void extractHost_handlesSrvUri() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        assertEquals("cluster0.mongodb.net", advice.extractHost("mongodb+srv://user:pass@cluster0.mongodb.net/db"));
    }
}

