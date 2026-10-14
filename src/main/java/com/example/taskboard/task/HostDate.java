package com.example.taskboard.task;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

/**
 * A date the host can store (TSK-DUE-DATE PIC 9(8), YYYYMMDD): its year has four digits, from
 * 0001-01-01 to 9999-12-31. Null is valid.
 */
@Documented
@Constraint(validatedBy = HostDate.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface HostDate {

  String message() default "must be a date from 0001-01-01 to 9999-12-31";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<HostDate, LocalDate> {

    @Override
    public boolean isValid(LocalDate date, ConstraintValidatorContext context) {
      return date == null || (date.getYear() >= 1 && date.getYear() <= 9999);
    }
  }
}
