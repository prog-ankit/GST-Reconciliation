package org.tally.gst_reconcillation.dto;

public class ReconciliationResultDto {
    public String jobId;
    public String fileName;
    public int missingInTally;
    public int missingInGST;
    public int mismatches;
}