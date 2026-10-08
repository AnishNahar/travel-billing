package com.example.travelbilling.web;

import com.example.travelbilling.common.FieldProblem;

import java.util.List;

/** Error body for every 4xx/5xx. existing_id is set on duplicate external_id conflicts. */
public record ApiError(String error, String message, List<FieldProblem> details, String existingId) {

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, null, null);
    }
}
