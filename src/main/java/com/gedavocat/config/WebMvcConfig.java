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
        // Webjars (Bootstrap, Font Awesome, jQuery)
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .resourceChain(false);

        // Static resources (CSS, JS, images)
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .resourceChain(false);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .resourceChain(false);

        registry.addResourceHandler("/img/**")
                .addResourceLocations("classpath:/static/img/")
                .resourceChain(false);

        registry.addResourceHandler("/favicon.svg")
                .addResourceLocations("classpath:/static/")
                .resourceChain(false);

        registry.addResourceHandler("/robots.txt")
                .addResourceLocations("classpath:/static/")
                .resourceChain(false);

        registry.addResourceHandler("/.well-known/**")
                .addResourceLocations("classpath:/static/.well-known/")
                .resourceChain(false);
    }
}
