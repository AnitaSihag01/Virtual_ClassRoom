package com.classroom.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Clean URLs (no .html) forward to the static pages, so /admin/home, /login, /room etc. never end in a 404. */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry r) {
        r.addViewController("/login").setViewName("forward:/login.html");
        r.addViewController("/change-password").setViewName("forward:/change-password.html");
        r.addViewController("/room").setViewName("forward:/room.html");
        r.addViewController("/replay").setViewName("forward:/replay.html");
        for (String role : new String[]{"admin", "teacher", "student"}) {
            r.addViewController("/" + role).setViewName("forward:/" + role + "/home.html");
            r.addViewController("/" + role + "/home").setViewName("forward:/" + role + "/home.html");
        }
    }
}
