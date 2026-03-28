package com.gedavocat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WebMvcConfig implements WebMvcConfigurer {

    private final ErrorStatusInterceptor errorStatusInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(errorStatusInterceptor).addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        int oneYear = 31536000;

        // Webjars (Bootstrap, Font Awesome, jQuery)
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .setCachePeriod(oneYear)
                .resourceChain(true);

        // Static resources (CSS, JS, images)
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCachePeriod(oneYear)
                .resourceChain(true);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCachePeriod(oneYear)
                .resourceChain(true);

        registry.addResourceHandler("/img/**")
                .addResourceLocations("classpath:/static/img/")
                .setCachePeriod(oneYear)
                .resourceChain(true);

        registry.addResourceHandler("/favicon.svg")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(oneYear)
                .resourceChain(true);

        registry.addResourceHandler("/robots.txt")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(86400)
                .resourceChain(true);

        registry.addResourceHandler("/.well-known/**")
                .addResourceLocations("classpath:/static/.well-known/")
                .setCachePeriod(86400)
                .resourceChain(true);

        // PWA manifest and service worker
        registry.addResourceHandler("/manifest.json")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(86400)
                .resourceChain(true);

        registry.addResourceHandler("/sw.js")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(0)
                .resourceChain(false);
    }
}
