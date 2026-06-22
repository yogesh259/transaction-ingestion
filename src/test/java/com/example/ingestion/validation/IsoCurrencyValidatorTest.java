package com.example.ingestion.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IsoCurrencyValidatorTest {

    private final IsoCurrencyValidator validator = new IsoCurrencyValidator();

    @Test
    void acceptsValidIsoCodes() {
        assertThat(validator.isValid("USD", null)).isTrue();
        assertThat(validator.isValid("EUR", null)).isTrue();
        assertThat(validator.isValid("GBP", null)).isTrue();
    }

    @Test
    void rejectsUnknownCode() {
        assertThat(validator.isValid("XYZ", null)).isFalse();
    }

    @Test
    void rejectsLowercase() {
        assertThat(validator.isValid("usd", null)).isFalse();
    }

    @Test
    void treatsNullAndBlankAsValid_deferredToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
    }
}

