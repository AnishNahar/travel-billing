package com.example.travelbilling.invoice;

import com.example.travelbilling.common.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Accepts {"transaction_id": "txn_1"} or {"transaction_ids": ["txn_1", ...]}. */
@Component
public class InvoiceRequestReader {

    static final int MAX_TRANSACTIONS = 100;

    public List<String> readTransactionIds(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw ValidationException.of("body", "must be a JSON object");
        }
        boolean single = body.has("transaction_id");
        boolean many = body.has("transaction_ids");
        if (single == many) {
            throw ValidationException.of("body", "provide exactly one of transaction_id or transaction_ids");
        }
        if (single) {
            JsonNode id = body.get("transaction_id");
            if (!id.isTextual()) {
                throw ValidationException.of("transaction_id", "must be a string");
            }
            return List.of(id.asText());
        }
        JsonNode ids = body.get("transaction_ids");
        if (!ids.isArray() || ids.isEmpty() || ids.size() > MAX_TRANSACTIONS) {
            throw ValidationException.of("transaction_ids",
                    "must be a non-empty array of at most " + MAX_TRANSACTIONS + " ids");
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            if (!ids.get(i).isTextual()) {
                throw ValidationException.of("transaction_ids[" + i + "]", "must be a string");
            }
            result.add(ids.get(i).asText());
        }
        return result;
    }
}
