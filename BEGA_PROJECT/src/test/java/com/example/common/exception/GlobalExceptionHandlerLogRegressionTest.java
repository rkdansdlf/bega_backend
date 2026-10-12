package com.example.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerLogRegressionTest {

    @Test
    void invalidRequestProducesOneValidationWarnFromAdvice() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        try {
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ValidationProbeController())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setValidator(validator)
                    .build();

            mockMvc.perform(post("/validation-probe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"value\":\"\"}"))
                    .andExpect(status().isBadRequest());

            assertThat(appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .filter(event -> event.getFormattedMessage().startsWith("Validation failed:")))
                    .hasSize(1);
        } finally {
            validator.destroy();
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @RestController
    static class ValidationProbeController {
        @PostMapping("/validation-probe")
        void validate(@Valid @RequestBody ValidationProbe probe) {
        }
    }

    static class ValidationProbe {
        @NotBlank
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
