package org.tally.gst_reconcillation.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.tally.gst_reconcillation.dto.ReconciliationResultDto;
import org.tally.gst_reconcillation.service.ReconciliationService;

import java.io.File;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReconciliationController {
    @Autowired
    private ReconciliationService service;

    @PostMapping("/reconcile")
    public ReconciliationResultDto reconcile(@RequestParam("tally") MultipartFile tallyFile, @RequestParam("gst") MultipartFile gstFile) throws Exception {
        String baseDir = System.getProperty("java.io.tmpdir");

        File dir = new File(baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String tallyPath = baseDir + "/" + UUID.randomUUID() + "_tally.xlsx";
        String gstPath   = baseDir + "/" + UUID.randomUUID() + "_gst.xlsx";
        tallyFile.transferTo(new File(tallyPath));
        gstFile.transferTo(new File(gstPath));
        return service.process(tallyPath, gstPath);
    }

    @GetMapping("/download")
    public ResponseEntity<UrlResource> downloadFile(@RequestParam String fileName) throws Exception {
        String baseDir = System.getProperty("java.io.tmpdir");
        File file = new File(baseDir + "/" + fileName);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        UrlResource resource = new UrlResource(file.toURI());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}
