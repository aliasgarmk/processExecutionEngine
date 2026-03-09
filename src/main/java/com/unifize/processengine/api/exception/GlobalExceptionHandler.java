package com.unifize.processengine.api.exception;

import com.unifize.processengine.exception.DefinitionValidationException;
import com.unifize.processengine.exception.DuplicateInstanceException;
import com.unifize.processengine.exception.FieldValidationException;
import com.unifize.processengine.exception.InactiveInstanceException;
import com.unifize.processengine.exception.InstanceNotFoundException;
import com.unifize.processengine.exception.InvalidTransitionException;
import com.unifize.processengine.exception.NoRoutingMatchException;
import com.unifize.processengine.exception.OptimisticLockException;
import com.unifize.processengine.exception.UnauthorisedTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DefinitionValidationException.class)
    public ProblemDetail handleDefinitionValidation(DefinitionValidationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Definition Validation Failed");
        pd.setProperty("violations", ex.violations());
        return pd;
    }

    @ExceptionHandler(FieldValidationException.class)
    public ProblemDetail handleFieldValidation(FieldValidationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "Field validation failed");
        pd.setTitle("Field Validation Failed");
        pd.setProperty("violations", ex.validationResult().violations());
        return pd;
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ProblemDetail handleInvalidTransition(InvalidTransitionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UnauthorisedTransitionException.class)
    public ProblemDetail handleUnauthorised(UnauthorisedTransitionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InactiveInstanceException.class)
    public ProblemDetail handleInactiveInstance(InactiveInstanceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Concurrent Modification");
        return pd;
    }

    @ExceptionHandler(InstanceNotFoundException.class)
    public ProblemDetail handleInstanceNotFound(InstanceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateInstanceException.class)
    public ProblemDetail handleDuplicateInstance(DuplicateInstanceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(NoRoutingMatchException.class)
    public ProblemDetail handleNoRouting(NoRoutingMatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        pd.setTitle("Bad Request");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
