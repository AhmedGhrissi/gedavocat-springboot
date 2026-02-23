package com.gedavocat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serve or redirect the browser request for /favicon.ico to the SVG favicon we ship.
 */
@Controller
public class FaviconController {

    @GetMapping("/favicon.ico")
    public String favicon() {
        // redirect to the existing /favicon.svg in static resources
        return "redirect:/favicon.svg";
    }
}
