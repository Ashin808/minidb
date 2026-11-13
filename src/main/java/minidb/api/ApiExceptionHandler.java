package minidb.api;

import minidb.sql.SqlException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/** The one place where exceptions become HTTP responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorResponse(String error) {}

    @ExceptionHandler(SqlException.class)
    public ResponseEntity<ErrorResponse> userError(SqlException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> badJson(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Request body must be JSON like {\"sql\": \"...\"}"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> diskError(IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Storage error: " + e.getMessage()));
    }
}