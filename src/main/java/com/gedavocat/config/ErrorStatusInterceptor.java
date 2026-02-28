package com.gedavocat.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * Interceptor that ensures when a controller returns a view named "error" the HTTP response
 * status is set to the status indicated on the ModelAndView (or the status attribute),
 * or defaults to 500. This avoids situations where an error page is rendered but the
 * HTTP status remains 200.
 */
@Component
@Slf4j
public class ErrorStatusInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        if (modelAndView == null) return;

        String viewName = modelAndView.getViewName();
        if (viewName == null) return;

        // Normalize view name (could be "error" or "client-portal/error" etc.)
        if (!viewName.toLowerCase().contains("error")) return;

        // Try to get status from ModelAndView.getStatus()
        HttpStatusCode status = modelAndView.getStatus();
        int code = -1;
        if (status != null) {
            try { code = status.value(); } catch (NoSuchMethodError e) { /* fallback below */ }
        }

        // If not available, try to parse a "status" model attribute (e.g. "500 - Erreur interne")
        if (code <= 0) {
            Map<String, Object> model = modelAndView.getModel();
            Object statusAttr = model.get("status");
            if (statusAttr != null) {
                String s = statusAttr.toString().trim();
                // Try to extract leading number
                String num = s.split("\\s+|-", 2)[0];
                try {
                    code = Integer.parseInt(num);
                } catch (Exception ignored) { }
            }
        }

        if (code <= 0) code = HttpStatus.INTERNAL_SERVER_ERROR.value();

        try {
            response.setStatus(code);
            log.debug("ErrorStatusInterceptor set response status={} for view='{}' uri={}", code, viewName, request.getRequestURI());
        } catch (Exception e) {
            log.warn("Failed to set response status to {} for uri {}: {}", code, request.getRequestURI(), e.getMessage());
        }
    }
}