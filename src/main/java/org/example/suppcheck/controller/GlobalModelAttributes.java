package org.example.suppcheck.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Stellt globale Model-Attribute für alle Views bereit (z.B. Versionsnummer).
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private static final String DEV_HOST = "mongodb-service.treasury.svc.cluster.local";

    @Value("${app.version:-}")
    private String appVersion;

    @Value("${app.build.timestamp:-}")
    private String buildTimestamp;

    @Value("${spring.mongodb.uri:}")
    private String mongoUri;

    @ModelAttribute("appVersion")
    public String appVersion() {
        return appVersion;
    }

    @ModelAttribute("buildTimestamp")
    public String buildTimestamp() {
        return buildTimestamp;
    }

    @ModelAttribute("mongoHost")
    public String mongoHost() {
        return extractHost(mongoUri);
    }

    @ModelAttribute("isProd")
    public boolean isProd() {
        String host = extractHost(mongoUri);
        return !host.isEmpty() && !host.contains(DEV_HOST);
    }

    String extractHost(String uri) {
        if (uri == null || uri.isBlank()) {
            return "";
        }
        // Format: mongodb://[user:pass@]host:port/db[?params]
        String work = uri;
        if (work.startsWith("mongodb://")) {
            work = work.substring("mongodb://".length());
        } else if (work.startsWith("mongodb+srv://")) {
            work = work.substring("mongodb+srv://".length());
        }
        // Remove credentials
        int atIdx = work.indexOf('@');
        if (atIdx >= 0) {
            work = work.substring(atIdx + 1);
        }
        // Remove path and params
        int slashIdx = work.indexOf('/');
        if (slashIdx >= 0) {
            work = work.substring(0, slashIdx);
        }
        return work;
    }
}

