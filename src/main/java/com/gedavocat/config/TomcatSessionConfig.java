package com.gedavocat.config;

import org.apache.catalina.Context;
import org.apache.catalina.session.StandardManager;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Disable Tomcat StandardManager persistence (SESSIONS.ser) to avoid EOFException
 * when Tomcat tries to read a corrupted persisted sessions file on restart.
 */
@Configuration
public class TomcatSessionConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addContextCustomizers((TomcatContextCustomizer) (Context context) -> {
            // Use a StandardManager with null pathname to disable session persistence to disk
            StandardManager manager = new StandardManager();
            manager.setPathname(null);
            context.setManager(manager);
        });
    }
}
