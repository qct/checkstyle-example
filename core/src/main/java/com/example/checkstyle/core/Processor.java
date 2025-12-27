package com.example.checkstyle.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** Created by qct on 27/12/2025. */
@Slf4j
public class Processor {
    private CompletableFuture<Void> scheduleOrderGroup(Long orderListId, List<Task> tasks, ExecutorService executor) {
        // 分组：WORKING vs PENDING
        Map<Boolean, List<Task>> partition = tasks.stream().collect(Collectors.partitioningBy(t -> isWorking(t.position) && !isFailed(t.status)));

        List<Task> working = partition.get(true);
        List<Task> pending = partition.get(false);

        log.info("OrderListId={} : {} WORKING, {} PENDING", orderListId, working.size(), pending.size());

        // WORKING 并发（捕获异常）
        CompletableFuture<Void> workFuture = CompletableFuture.allOf(working.stream()
                .map(t -> CompletableFuture.runAsync(() -> safeRun(t.runnable), executor))
                .toArray(CompletableFuture[]::new));

        // WORKING 完成后执行 pending（捕获异常）
        return workFuture.thenCompose(v -> pending.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(pending.stream()
                        .map(t -> CompletableFuture.runAsync(() -> safeRun(t.runnable), executor))
                        .toArray(CompletableFuture[]::new)));
    }

    private void safeRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.error("Task execution failed", e);
        }
    }

    private boolean isFailed(String status) {
        return false;
    }

    private boolean isWorking(String position) {
        return false;
    }

    private record Task(Long orderListId, String position, String status, Runnable runnable) {}
}
