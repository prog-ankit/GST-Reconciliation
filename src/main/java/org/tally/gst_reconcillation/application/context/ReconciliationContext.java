package org.tally.gst_reconcillation.application.context;

import org.tally.gst_reconcillation.model.InvoiceRecord;

import java.util.Map;

public class ReconciliationContext {
    public Map<String, InvoiceRecord> tallyMap;
    public Map<String, InvoiceRecord> gstMap;
}