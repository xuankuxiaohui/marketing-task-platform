package com.mkt.spike.misc;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLogSmokeTest {

    @Test
    void jsonContainsTraceId() throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("spike.misc");
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);
        encoder.start();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("traceId", "trace-spike-8");
        try {
            logger.info("hello-spike");
            ILoggingEvent event = appender.list.getFirst();
            byte[] bytes = encoder.encode(event);
            String json = new String(bytes, StandardCharsets.UTF_8);
            assertTrue(json.contains("trace-spike-8"));
            assertTrue(json.contains("hello-spike"));
        } finally {
            MDC.clear();
            logger.detachAppender(appender);
            encoder.stop();
        }
    }
}
