package com.example.ingestion.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;

public class IsoCurrencyValidator implements ConstraintValidator<IsoCurrency, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null/blank handled by @NotBlank so other constraints can report independently.
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            Currency.getInstance(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

