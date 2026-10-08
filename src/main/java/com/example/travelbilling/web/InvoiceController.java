package com.example.travelbilling.web;

import com.example.travelbilling.common.NotFoundException;
import com.example.travelbilling.common.Page;
import com.example.travelbilling.common.PageRequest;
import com.example.travelbilling.invoice.Invoice;
import com.example.travelbilling.invoice.InvoiceId;
import com.example.travelbilling.invoice.InvoiceService;
import com.example.travelbilling.invoice.InvoiceSummary;
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
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Invoice> create(@RequestBody JsonNode body) {
        Invoice created = service.create(body);
        return ResponseEntity.created(URI.create("/invoices/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public Invoice get(@PathVariable String id) {
        return service.get(parseId(id));
    }

    @GetMapping
    public Page<InvoiceSummary> list(@RequestParam(required = false) Integer limit,
                                     @RequestParam(required = false) String cursor) {
        PageRequest request = PageParams.parse(limit, cursor, raw -> InvoiceId.parse(raw).map(InvoiceId::value));
        return PageParams.page(service.list(request), request, summary -> summary.id().toString());
    }

    @PatchMapping("/{id}")
    public Invoice update(@PathVariable String id, @RequestBody JsonNode body) {
        return service.update(parseId(id), body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    private static InvoiceId parseId(String raw) {
        return InvoiceId.parse(raw).orElseThrow(() -> new NotFoundException("Invoice " + raw + " not found"));
    }
}
