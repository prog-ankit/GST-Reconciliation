package org.tally.gst_reconcillation.application.context;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReconciliationCache {

    private final Map<String, ReconciliationContext> reconciliationContextMap = new ConcurrentHashMap<>();

    public void put(String jobId, ReconciliationContext ctx) {
        reconciliationContextMap.put(jobId, ctx);
    }

    public ReconciliationContext get(String jobId) {
        return reconciliationContextMap.get(jobId);
    }

    public void remove(String jobId) {
        reconciliationContextMap.remove(jobId);
    }
}