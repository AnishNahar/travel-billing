package com.example.travelbilling.transaction;

import com.example.travelbilling.common.FieldProblem;
import com.example.travelbilling.common.Money;
import com.example.travelbilling.common.ValidationException;
import com.example.travelbilling.transaction.metadata.HotelMetadata;
import com.example.travelbilling.transaction.metadata.TransactionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.regex.Pattern;

/** Turns a JSON request body into a validated {@link TransactionData}, collecting every problem into one 400. */
@Component
public class TransactionRequestReader {

    private static final List<String> PATCHABLE_FIELDS =
            List.of("currency", "occurred_at", "total", "tax_lines", "fee_lines", "metadata");
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");
    private static final int MAX_EXTERNAL_ID_LENGTH = 255;

    private final ObjectMapper mapper;
    private final Validator validator;

    public TransactionRequestReader(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    /** Reads only the identity field, so duplicates can be answered before the rest of the body is validated. */
    public String requireExternalId(JsonNode body) {
        requireObject(body);
        List<FieldProblem> problems = new ArrayList<>();
        String externalId = readExternalId(body, problems);
        throwIfAny(problems);
        return externalId;
    }

    public TransactionData read(JsonNode body) {
        requireObject(body);
        List<FieldProblem> problems = new ArrayList<>();

        TransactionType type = readType(body, problems);
        String externalId = readExternalId(body, problems);
        String currency = readCurrency(body, problems);
        Instant occurredAt = readInstant(body, "occurred_at", problems);
        BigDecimal total = readMoney(body, "total", problems);
        List<TaxLine> taxLines = readList(body, "tax_lines", TaxLine.class, problems);
        List<FeeLine> feeLines = readList(body, "fee_lines", FeeLine.class, problems);
        TransactionMetadata metadata = type == null ? null : readMetadata(body, type, problems);

        throwIfAny(problems);
        return new TransactionData(type, externalId, currency, occurredAt, total, taxLines, feeLines, metadata);
    }

    /**
     * PATCH: overlays the supplied fields onto the stored transaction and validates the result as a whole.
     * Lists and metadata are replaced, not merged. type and external_id are immutable.
     */
    public TransactionData readPatch(Transaction current, JsonNode patch) {
        requireObject(patch);
        List<FieldProblem> problems = new ArrayList<>();
        rejectChange(patch, "type", current.type().wireName(), problems);
        rejectChange(patch, "external_id", current.externalId(), problems);
        if (PATCHABLE_FIELDS.stream().noneMatch(patch::has)) {
            problems.add(new FieldProblem("body", "must contain at least one of " + PATCHABLE_FIELDS));
        }
        throwIfAny(problems);

        ObjectNode merged = mapper.valueToTree(current);
        PATCHABLE_FIELDS.stream().filter(patch::has).forEach(field -> merged.set(field, patch.get(field)));
        return read(merged);
    }

    private static void requireObject(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw ValidationException.of("body", "must be a JSON object");
        }
    }

    private static void throwIfAny(List<FieldProblem> problems) {
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
    }

    private static void rejectChange(JsonNode patch, String field, String storedValue, List<FieldProblem> problems) {
        JsonNode node = patch.get(field);
        if (node != null && !storedValue.equals(node.asText())) {
            problems.add(new FieldProblem(field, "cannot be changed"));
        }
    }

    private static TransactionType readType(JsonNode body, List<FieldProblem> problems) {
        JsonNode node = body.get("type");
        TransactionType type = node != null && node.isTextual()
                ? TransactionType.fromWire(node.asText()).orElse(null)
                : null;
        if (type == null) {
            problems.add(new FieldProblem("type", "must be one of flight, hotel, rail, navan_fee"));
        }
        return type;
    }

    private static String readExternalId(JsonNode body, List<FieldProblem> problems) {
        JsonNode node = body.get("external_id");
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            problems.add(new FieldProblem("external_id", "is required"));
            return null;
        }
        if (node.asText().length() > MAX_EXTERNAL_ID_LENGTH) {
            problems.add(new FieldProblem("external_id", "must be at most " + MAX_EXTERNAL_ID_LENGTH + " characters"));
            return null;
        }
        return node.asText();
    }

    private static String readCurrency(JsonNode body, List<FieldProblem> problems) {
        JsonNode node = body.get("currency");
        String code = node != null && node.isTextual() ? node.asText() : null;
        if (code == null || !CURRENCY_CODE.matcher(code).matches() || !isKnownCurrency(code)) {
            problems.add(new FieldProblem("currency", "must be an ISO-4217 code such as EUR"));
            return null;
        }
        return code;
    }

    private static boolean isKnownCurrency(String code) {
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Instant readInstant(JsonNode body, String field, List<FieldProblem> problems) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual()) {
            problems.add(new FieldProblem(field, "is required (ISO-8601, e.g. 2026-03-11T06:40:00Z)"));
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (DateTimeException e) {
            problems.add(new FieldProblem(field, "must be an ISO-8601 timestamp with offset, e.g. 2026-03-11T06:40:00Z"));
            return null;
        }
    }

    private static BigDecimal readMoney(JsonNode body, String field, List<FieldProblem> problems) {
        JsonNode node = body.get(field);
        if (node == null || !node.isNumber()) {
            problems.add(new FieldProblem(field, "is required and must be a number"));
            return null;
        }
        BigDecimal amount = node.decimalValue();
        String problem = Money.problemWith(amount);
        if (problem != null) {
            problems.add(new FieldProblem(field, problem));
            return null;
        }
        return Money.normalize(amount);
    }

    private <T> List<T> readList(JsonNode body, String field, Class<T> itemType, List<FieldProblem> problems) {
        JsonNode node = body.get(field);
        if (node == null || !node.isArray()) {
            problems.add(new FieldProblem(field, "is required (use [] when there are none)"));
            return null;
        }
        List<T> items = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            T item = convert(node.get(i), itemType, field + "[" + i + "]", problems);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private TransactionMetadata readMetadata(JsonNode body, TransactionType type, List<FieldProblem> problems) {
        TransactionMetadata metadata = convert(body.get("metadata"), type.metadataClass(), "metadata", problems);
        if (metadata instanceof HotelMetadata hotel
                && hotel.checkIn() != null && hotel.checkOut() != null
                && !hotel.checkOut().isAfter(hotel.checkIn())) {
            problems.add(new FieldProblem("metadata.check_out", "must be after check_in"));
        }
        return metadata;
    }

    private <T> T convert(JsonNode node, Class<T> type, String path, List<FieldProblem> problems) {
        if (node == null || !node.isObject()) {
            problems.add(new FieldProblem(path, "is required and must be an object"));
            return null;
        }
        T value;
        try {
            value = mapper.treeToValue(node, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            problems.add(new FieldProblem(path + jsonPath(e), describe(e)));
            return null;
        }
        for (ConstraintViolation<T> violation : validator.validate(value)) {
            problems.add(new FieldProblem(path + "." + snakeCase(violation.getPropertyPath().toString()), violation.getMessage()));
        }
        return value;
    }

    private static String jsonPath(Exception e) {
        if (!(e instanceof JsonMappingException mapping)) {
            return "";
        }
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : mapping.getPath()) {
            path.append(ref.getFieldName() != null ? "." + ref.getFieldName() : "[" + ref.getIndex() + "]");
        }
        return path.toString();
    }

    private static String describe(Exception e) {
        if (e.getCause() instanceof IllegalArgumentException cause && cause.getMessage() != null) {
            return cause.getMessage();
        }
        if (e instanceof InvalidFormatException invalid) {
            return "has an invalid value '" + invalid.getValue() + "'";
        }
        if (e instanceof MismatchedInputException) {
            return "has the wrong type or format";
        }
        return "is invalid";
    }

    private static String snakeCase(String javaPath) {
        return javaPath.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
