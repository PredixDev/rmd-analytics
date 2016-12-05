package com.ge.predix.solsvc.refappanalytic.boot;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.RestController;

/**
 * 
 * @author predix -
 */
@RestController
@EnableAutoConfiguration
@Configuration
@ComponentScan(
{
    "com.ge.predix.solsvc.refappanalytic.service", "com.ge.predix.solsvc.refappanalytic.analytic",
    "com.ge.predix.solsvc.timeseries", "com.ge.predix.solsvc.websocket"
})
@ImportResource(
{
    "classpath:/META-INF/spring/predix-rest-client-scan-context.xml",
    "classpath:/META-INF/spring/ext-util-scan-context.xml",
    "classpath:/META-INF/spring/fdh-bootstrap-client-scan-context.xml"
})
@PropertySource("classpath:application-default.properties")
public class RefAppAnalyticApplication
{

    private static final Logger log = LoggerFactory.getLogger(RefAppAnalyticApplication.class);

    /**
     * @param args
     *            -
     */
    @SuppressWarnings("nls")
    public static void main(String[] args)
    {
        log.info("Let's inspect the beans provided by Spring Boot:");

        ApplicationContext ctx = SpringApplication.run(RefAppAnalyticApplication.class, args);
        log.info("Let's inspect the beans provided by Spring Boot:");

        String[] beanNames = ctx.getBeanDefinitionNames();
        Arrays.sort(beanNames);
        for (String beanName : beanNames)
        {
            log.info(beanName);
        }
        
        log.debug("Let's inspect the profiles provided by Spring Boot:");
        String profiles[] = ctx.getEnvironment().getActiveProfiles();
        for (int i = 0; i < profiles.length; i++)
            log.debug("profile=" + profiles[i]);
    }

    /**
     * @return -
     */
    @Bean
    public TomcatEmbeddedServletContainerFactory tomcatEmbeddedServletContainerFactory()
    {
        return new TomcatEmbeddedServletContainerFactory();
    }
}
