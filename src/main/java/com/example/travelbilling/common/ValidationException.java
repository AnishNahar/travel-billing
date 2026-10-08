package com.example.travelbilling.common;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<FieldProblem> problems;

    public ValidationException(List<FieldProblem> problems) {
        super("Request is invalid: " + problems);
        this.problems = List.copyOf(problems);
    }

    public static ValidationException of(String field, String message) {
        return new ValidationException(List.of(new FieldProblem(field, message)));
    }

    public List<FieldProblem> problems() {
        return problems;
    }
}
