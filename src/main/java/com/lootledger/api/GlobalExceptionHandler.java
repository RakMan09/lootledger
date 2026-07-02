package com.lootledger.api;

import com.lootledger.idempotency.IdempotencyExceptions;
import com.lootledger.ledger.LedgerException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LedgerException.class)
    public ResponseEntity<ApiError> handleLedger(LedgerException e) {
        return ResponseEntity.unprocessableEntity().body(ApiError.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(IdempotencyExceptions.KeyReusedException.class)
    public ResponseEntity<ApiError> handleKeyReused(IdempotencyExceptions.KeyReusedException e) {
        return ResponseEntity.unprocessableEntity().body(ApiError.of("IDEMPOTENCY_KEY_REUSED", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyExceptions.InFlightException.class)
    public ResponseEntity<ApiError> handleInFlight(IdempotencyExceptions.InFlightException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", "1")
                .body(ApiError.of("REQUEST_IN_FLIGHT", e.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("MISSING_HEADER", "Missing required header: " + e.getHeaderName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> details = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> details.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", "Request validation failed", details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST", e.getMessage()));
    }
}
