package com.example.travelbilling.web;

import com.example.travelbilling.common.NotFoundException;
import com.example.travelbilling.common.Page;
import com.example.travelbilling.common.PageRequest;
import com.example.travelbilling.transaction.Transaction;
import com.example.travelbilling.transaction.TransactionId;
import com.example.travelbilling.transaction.TransactionService;
import com.example.travelbilling.transaction.TransactionSummary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Transaction> create(@RequestBody JsonNode body) {
        Transaction created = service.create(body);
        return ResponseEntity.created(URI.create("/transactions/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public Transaction get(@PathVariable String id) {
        return service.get(parseId(id));
    }

    @GetMapping
    public Page<TransactionSummary> list(@RequestParam(required = false) Integer limit,
                                         @RequestParam(required = false) String cursor) {
        PageRequest request = PageParams.parse(limit, cursor, raw -> TransactionId.parse(raw).map(TransactionId::value));
        return PageParams.page(service.list(request), request, summary -> summary.id().toString());
    }

    @PatchMapping("/{id}")
    public Transaction update(@PathVariable String id, @RequestBody JsonNode body) {
        return service.update(parseId(id), body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    private static TransactionId parseId(String raw) {
        return TransactionId.parse(raw).orElseThrow(() -> new NotFoundException("Transaction " + raw + " not found"));
    }
}
