package com.example.taskboard;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Answers every error with an RFC 9457 problem detail ({@code application/problem+json}): Spring
 * MVC's own exceptions and {@link org.springframework.web.ErrorResponse}s, such as an unknown task,
 * with their status; an invalid request body with 400 and a message per invalid field in {@code
 * errors}; anything else with 500.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String INVALID_FIELDS = "The request has invalid fields.";

  /** Bean Validation rejected the body. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    // Sorted, so that two messages for one field always come in the same order.
    Map<String, String> errors = new TreeMap<>();
    ex.getBindingResult().getFieldErrors().stream()
        .sorted(Comparator.comparing(FieldError::getDefaultMessage))
        .forEach(
            error ->
                errors.merge(error.getField(), error.getDefaultMessage(), (a, b) -> a + "; " + b));
    return badRequest(ex, INVALID_FIELDS, errors, headers, request);
  }

  /** The body is missing or not JSON, or a field holds a value of the wrong type. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (ex.getCause() instanceof MismatchedInputException mismatch) {
      String field =
          mismatch.getPath().stream()
              .map(JacksonException.Reference::getPropertyName)
              .filter(Objects::nonNull)
              .collect(Collectors.joining("."));
      if (!field.isEmpty()) {
        Map<String, String> errors = Map.of(field, invalidValue(mismatch.getTargetType()));
        return badRequest(ex, INVALID_FIELDS, errors, headers, request);
      }
    }
    return badRequest(ex, "The request body is missing or not valid JSON.", null, headers, request);
  }

  /** A bug: logged with its stack trace, answered without details. */
  @ExceptionHandler
  ProblemDetail handleUnexpected(Exception ex) {
    logger.error("Unexpected error while handling a request", ex);
    return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<Object> badRequest(
      Exception ex,
      String detail,
      Map<String, String> errors,
      HttpHeaders headers,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    if (errors != null) {
      problem.setProperty("errors", errors);
    }
    return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  private static String invalidValue(Class<?> type) {
    if (type == LocalDate.class) {
      return "must be a date like 2026-10-31";
    }
    if (type != null && type.isEnum()) {
      return Arrays.stream(type.getEnumConstants())
          .map(Object::toString)
          .collect(Collectors.joining(", ", "must be one of ", ""));
    }
    return "has an invalid value";
  }
}
