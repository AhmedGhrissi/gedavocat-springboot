package com.gedavocat.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;

/**
 * Génère le sitemap XML dynamique de l'application.
 * Seules les pages publiques sont référencées.
 * Accessible à : GET /sitemap.xml
 */
@Controller
public class SitemapController {

    @Value("${app.base-url:https://docavocat.fr}")
    private String baseUrl;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        String today = LocalDate.now().toString();
        String base  = baseUrl.replaceAll("/$", ""); // supprimer trailing slash

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n" +
               "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
               "        xsi:schemaLocation=\"http://www.sitemaps.org/schemas/sitemap/0.9\n" +
               "          http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd\">\n" +

               url(base + "/",                         today, "weekly",  "1.0") +
               url(base + "/landing",                  today, "weekly",  "0.9") +
               url(base + "/register",                 today, "monthly", "0.8") +
               url(base + "/login",                    today, "monthly", "0.5") +
               url(base + "/legal/terms",              today, "yearly",  "0.3") +
               url(base + "/legal/privacy",            today, "yearly",  "0.3") +

               "</urlset>";
    }

    private String url(String loc, String lastmod, String changefreq, String priority) {
        return "  <url>\n" +
               "    <loc>" + loc + "</loc>\n" +
               "    <lastmod>" + lastmod + "</lastmod>\n" +
               "    <changefreq>" + changefreq + "</changefreq>\n" +
               "    <priority>" + priority + "</priority>\n" +
               "  </url>\n";
    }
}
