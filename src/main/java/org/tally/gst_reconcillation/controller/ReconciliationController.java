package org.tally.gst_reconcillation.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.tally.gst_reconcillation.dto.FilterRequestDto;
import org.tally.gst_reconcillation.dto.ReconciliationResultDto;
import org.tally.gst_reconcillation.service.ReconciliationService;

import java.io.File;
import java.util.UUID;
@RestController
@CrossOrigin(origins = "https://gst-reconciliation-0sqz.onrender.com")
@RequestMapping("/api/reconcile")
public class ReconciliationController {

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    // ================= STEP 1 =================
    @PostMapping
    public ReconciliationResultDto reconcile(
            @RequestParam("tally") MultipartFile tallyFile,
            @RequestParam("gst") MultipartFile gstFile,
            @RequestParam("tolerance") double tolerance
    ) throws Exception {
        String baseDir = System.getProperty("java.io.tmpdir");

        File dir = new File(baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String tallyPath = baseDir + "/" + UUID.randomUUID() + "_tally.xlsx";
        String gstPath = baseDir + "/" + UUID.randomUUID() + "_gst.xlsx";
        tallyFile.transferTo(new File(tallyPath));
        gstFile.transferTo(new File(gstPath));
        return service.process(tallyPath, gstPath, tolerance);
    }

    // ================= STEP 2 =================
    @PostMapping("/filter")
    public ReconciliationResultDto filter(@RequestBody FilterRequestDto request) throws Exception {
        return service.filterByField(request.jobId, request.field);
    }
}