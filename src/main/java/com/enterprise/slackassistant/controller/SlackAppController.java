package com.enterprise.slackassistant.controller;

import com.slack.api.bolt.App;
import com.slack.api.bolt.jakarta_servlet.SlackAppServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackAppController {

    private static final Logger log = LoggerFactory.getLogger(SlackAppController.class);

    @Bean
    public ServletRegistrationBean<SlackAppServlet> slackAppServlet(App app) {
        SlackAppServlet servlet = new SlackAppServlet(app);
        ServletRegistrationBean<SlackAppServlet> registration =
                new ServletRegistrationBean<>(servlet, "/slack/events");
        registration.setName("slackAppServlet");
        registration.setLoadOnStartup(1);
        log.info("Slack Bolt servlet registered at /slack/events");
        return registration;
    }
}
