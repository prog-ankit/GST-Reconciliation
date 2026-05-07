package org.tally.gst_reconcillation.controller;

import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@RestController
@RequestMapping("/api")
public class DownloadController {

    @GetMapping("/download")
    public ResponseEntity<UrlResource> downloadFile(@RequestParam String fileName) throws Exception {

        String filePath = System.getProperty("java.io.tmpdir") + "/" + fileName;

        File file = new File(filePath);

        if (!file.exists()) {
            throw new RuntimeException("File not found");
        }

        UrlResource resource = new UrlResource(file.toURI());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}