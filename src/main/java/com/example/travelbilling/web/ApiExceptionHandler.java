package com.example.travelbilling.web;

import com.example.travelbilling.common.ConflictException;
import com.example.travelbilling.common.NotFoundException;
import com.example.travelbilling.common.ValidationException;
import com.example.travelbilling.transaction.DuplicateExternalIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiError> validation(ValidationException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("validation_failed", "Request is invalid", e.problems(), null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(ApiError.of("bad_request", "Malformed request: " + rootMessage(e)));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("not_found", e.getMessage()));
    }

    @ExceptionHandler(DuplicateExternalIdException.class)
    ResponseEntity<ApiError> duplicate(DuplicateExternalIdException e) {
        String existingId = e.existingId() == null ? null : e.existingId().toString();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(e.code(), e.getMessage(), null, existingId));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.internalServerError().body(ApiError.of("internal_error", "Unexpected server error"));
    }

    private static String rootMessage(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return message.lines().findFirst().orElse(message);
    }
}
