package com.example.taskboard.task;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A host user id (TSK-USER PIC X(8), upper case on the host) in lower case: a letter followed by up
 * to seven letters or digits. Null is valid.
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "[a-z][a-z0-9]{0,7}")
@ReportAsSingleViolation
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface UserId {

  String message() default "must be a lower-case user id: a letter, then up to 7 letters or digits";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
