package com.example.travelbilling;

import com.example.travelbilling.transaction.TransactionData;
import com.example.travelbilling.transaction.TransactionRepository;
import com.example.travelbilling.transaction.TransactionRequestReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Required test 6
class ConcurrentCreateTest extends ApiTestBase {

    private static final int ROUNDS = 10;
    private static final int CONCURRENT_POSTS = 8;

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private TransactionRequestReader reader;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void overlappingPostsWithTheSameExternalIdLeaveExactlyOneRow() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_POSTS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String externalId = "race-" + round;
                ObjectNode body = seed("flight").put("external_id", externalId);
                CountDownLatch start = new CountDownLatch(1);

                List<Future<ApiClient.Response>> calls = new ArrayList<>();
                for (int i = 0; i < CONCURRENT_POSTS; i++) {
                    calls.add(pool.submit(() -> {
                        start.await();
                        return api.post("/transactions", body);
                    }));
                }
                start.countDown();

                List<ApiClient.Response> responses = new ArrayList<>();
                for (Future<ApiClient.Response> call : calls) {
                    responses.add(call.get());
                }

                List<ApiClient.Response> created = responses.stream().filter(r -> r.status() == 201).toList();
                List<ApiClient.Response> rejected = responses.stream().filter(r -> r.status() == 409).toList();
                assertThat(created).as("round %d: %s", round, responses).hasSize(1);
                assertThat(rejected).hasSize(CONCURRENT_POSTS - 1);
                String winner = created.getFirst().body().get("id").asText();
                rejected.forEach(r -> assertThat(r.body().get("existing_id").asText()).isEqualTo(winner));
                assertThat(rowsWithExternalId(externalId)).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theDatabaseConstraintItselfRejectsASecondInsert() {
        // Bypasses the service's pre-check to prove uniqueness lives in the database, not in application code.
        TransactionData data = reader.read(seed("flight"));
        transactionTemplate.executeWithoutResult(s -> repository.insert(data, Instant.now()));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s -> repository.insert(data, Instant.now())))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(rowsWithExternalId(data.externalId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flight_coupons", Integer.class)).isEqualTo(1);
    }
}
