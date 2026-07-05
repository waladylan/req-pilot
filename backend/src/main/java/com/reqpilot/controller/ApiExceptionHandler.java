package com.reqpilot.controller;

import com.reqpilot.dto.ErrorResponse;
import com.reqpilot.ai.AiErrorType;
import com.reqpilot.ai.AiProviderException;
import com.reqpilot.service.ResourceNotFoundException;
import com.reqpilot.wfm.WfmValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
    List<String> details =
        exception.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .toList();
    return ResponseEntity.badRequest().body(ErrorResponse.of("Validation failed", details));
  }

  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.of(exception.getMessage(), List.of(exception.getMessage())));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of(exception.getMessage(), List.of(exception.getMessage())));
  }

  @ExceptionHandler(WfmValidationException.class)
  public ResponseEntity<ErrorResponse> handleWfmValidation(WfmValidationException exception) {
    List<String> details =
        exception.errors().stream()
            .map((error) -> "%s %s: %s".formatted(error.path(), error.code(), error.message()))
            .toList();
    return ResponseEntity.badRequest().body(ErrorResponse.of("Invalid WFM document", details));
  }

  @ExceptionHandler(AiProviderException.class)
  public ResponseEntity<ErrorResponse> handleAiProvider(AiProviderException exception) {
    return ResponseEntity.status(status(exception.errorType()))
        .body(ErrorResponse.of("AI provider error", List.of(exception.reasonCode(), exception.getMessage())));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of("Unexpected server error", List.of(exception.getMessage())));
  }

  private String formatFieldError(FieldError error) {
    return "%s: %s".formatted(error.getField(), error.getDefaultMessage());
  }

  private HttpStatus status(AiErrorType errorType) {
    return switch (errorType) {
      case INVALID_API_KEY -> HttpStatus.UNAUTHORIZED;
      case PAYMENT_REQUIRED -> HttpStatus.PAYMENT_REQUIRED;
      case OPENROUTER_RATE_LIMIT, PROVIDER_RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
      case PROVIDER_OVERLOADED, PROVIDER_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
      case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
      case INVALID_RESPONSE, UNKNOWN -> HttpStatus.BAD_GATEWAY;
    };
  }
}
