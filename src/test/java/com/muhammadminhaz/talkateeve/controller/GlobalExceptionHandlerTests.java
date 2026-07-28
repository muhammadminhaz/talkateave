package com.muhammadminhaz.talkateeve.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTests {

    private GlobalExceptionHandler handler;
    private WebRequest request;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/bots/widget/ask");

        // Capture what actually reaches the log: the whole point of this handler is that
        // no failure is invisible, so the assertion has to be on the log, not the body.
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private ILoggingEvent onlyEvent() {
        assertEquals(1, appender.list.size(), "expected exactly one log event");
        return appender.list.getFirst();
    }

    @Test
    void handlesUnexpectedException_asServerErrorLoggedWithStackTrace() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAny(new IllegalStateException("embedding model unavailable"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.get("errorId"), "errorId must correlate the response with the log line");
        assertEquals("embedding model unavailable", body.get("message"));

        ILoggingEvent event = onlyEvent();
        assertEquals(Level.ERROR, event.getLevel());
        assertNotNull(event.getThrowableProxy(), "the throwable must be logged, not just its message");
        assertTrue(event.getFormattedMessage().contains(body.get("errorId").toString()));
        assertTrue(event.getFormattedMessage().contains("uri=/api/bots/widget/ask"));
    }

    @Test
    void handlesIllegalArgument_asBadRequestLoggedAtWarn() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleBadRequest(new IllegalArgumentException("Invalid UUID string: abc"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));

        ILoggingEvent event = onlyEvent();
        assertEquals(Level.WARN, event.getLevel(), "client errors must not page anyone");
        assertNotNull(event.getThrowableProxy());
    }

    @Test
    void handlesMaxUploadSize_asPayloadTooLarge() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleTooLarge(new MaxUploadSizeExceededException(5_000_000L), request);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertFalse(appender.list.isEmpty(), "an oversized upload must still be logged");
    }

    @Test
    void handlesResponseStatusException_preservingItsStatus() {
        ResponseEntity<Map<String, Object>> response = handler.handleStatus(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Level.WARN, onlyEvent().getLevel());
    }

    @Test
    void fallsBackToTheStatusReason_whenTheExceptionHasNoMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleAny(new RuntimeException(), request);

        assertEquals("Internal Server Error", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }
}
