package org.example.suppcheck.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Stellt globale Model-Attribute für alle Views bereit (z.B. Versionsnummer).
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @Value("${app.version:-}")
    private String appVersion;

    @Value("${app.build.timestamp:-}")
    private String buildTimestamp;

    @ModelAttribute("appVersion")
    public String appVersion() {
        return appVersion;
    }

    @ModelAttribute("buildTimestamp")
    public String buildTimestamp() {
        return buildTimestamp;
    }
}

