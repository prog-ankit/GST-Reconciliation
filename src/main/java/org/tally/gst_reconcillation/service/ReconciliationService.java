package org.tally.gst_reconcillation.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.tally.gst_reconcillation.application.context.ReconciliationCache;
import org.tally.gst_reconcillation.application.context.ReconciliationContext;
import org.tally.gst_reconcillation.dto.ReconciliationResultDto;
import org.tally.gst_reconcillation.model.InvoiceRecord;
import org.tally.gst_reconcillation.util.GeneralUtility;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;

@Service
public class ReconciliationService {

    private final ReconciliationCache cache;

    public ReconciliationService(ReconciliationCache cache) {
        this.cache = cache;
    }

    // ================= STEP 1: FULL PROCESS =================
    public ReconciliationResultDto process(String tallyFile,
                                           String gstFile,
                                           double tolerance) throws Exception {

        Map<String, InvoiceRecord> tallyMap = loadFileToMap(tallyFile);
        Map<String, InvoiceRecord> gstMap = loadFileToMap(gstFile);

        ReconciliationContext ctx = new ReconciliationContext();
        ctx.tallyMap = tallyMap;
        ctx.gstMap = gstMap;

        String jobId = UUID.randomUUID().toString();
        cache.put(jobId, ctx);

        List<InvoiceRecord> missingInTally = new ArrayList<>();
        List<InvoiceRecord> missingInGST = new ArrayList<>();
        List<InvoiceRecord> mismatches = new ArrayList<>();

        for (Map.Entry<String, InvoiceRecord> entry : gstMap.entrySet()) {
            String key = entry.getKey();
            InvoiceRecord tallyRec = tallyMap.get(key);

            if (tallyRec == null) {
                missingInTally.add(entry.getValue());
            } else if (GeneralUtility.isMismatch(entry.getValue(), tallyRec, tolerance)) {
                mismatches.add(entry.getValue());
            }
        }

        for (Map.Entry<String, InvoiceRecord> entry : tallyMap.entrySet()) {
            if (!gstMap.containsKey(entry.getKey())) {
                missingInGST.add(entry.getValue());
            }
        }

        String baseDir = System.getProperty("java.io.tmpdir");
        String fileName = "Reconciliation_Report_" + System.currentTimeMillis() + ".xlsx";
        String outputPath = baseDir + "/" + fileName;

        writeFinalReport(
                outputPath,
                missingInTally,
                missingInGST,
                mismatches,
                tallyMap,
                tolerance
        );

        // RESPONSE
        ReconciliationResultDto dto = new ReconciliationResultDto();
        dto.jobId = jobId;
        dto.fileName = fileName;
        dto.missingInTally = missingInTally.size();
        dto.missingInGST = missingInGST.size();
        dto.mismatches = mismatches.size();

        return dto;
    }

    // ================= STEP 2: FILTER REPORT (NEW ONLY) =================
    public ReconciliationResultDto filterByField(String jobId, String field) throws Exception {
        ReconciliationContext ctx = cache.get(jobId);

        Map<String, Double> tallyTotals = buildFilterFieldData(ctx.tallyMap, field);
        Map<String, Double> gstTotals = buildFilterFieldData(ctx.gstMap, field);

        Set<String> allGSTINs = new HashSet<>();
        allGSTINs.addAll(tallyTotals.keySet());
        allGSTINs.addAll(gstTotals.keySet());

        Set<String> matchedGSTINs = new HashSet<>();
        for (String gstin : allGSTINs) {
            double tallyTotal = tallyTotals.getOrDefault(gstin, 0.0);
            double gstTotal = gstTotals.getOrDefault(gstin, 0.0);

            if (Math.abs(tallyTotal - gstTotal) < 0.0001) {
                matchedGSTINs.add(gstin);
            }
        }
        String fileName = generateFilteredExcel(ctx, matchedGSTINs);
        ReconciliationResultDto dto = new ReconciliationResultDto();
        dto.fileName = fileName;
        return dto;
    }

    // ================= FINAL REPORT =================
    private void writeFinalReport(String filePath,
                                  List<InvoiceRecord> missingInTally,
                                  List<InvoiceRecord> missingInGST,
                                  List<InvoiceRecord> mismatches,
                                  Map<String, InvoiceRecord> tallyMap,
                                  double tolerance) throws Exception {

        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.setCompressTempFiles(true);

            CellStyle redStyle = workbook.createCellStyle();
            redStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            writeNormalSheet(workbook, "Missing_In_Tally", missingInTally);
            writeNormalSheet(workbook, "Missing_In_GST", missingInGST);
            writeMismatchSheet(workbook, mismatches, tallyMap, redStyle, tolerance);

            workbook.write(fos);
        } finally {
            workbook.dispose();
        }
    }

    // ================= Missing In Tally / Missing in GST SHEET =================
    private void writeNormalSheet(Workbook workbook,
                                  String sheetName,
                                  List<InvoiceRecord> data) {

        Sheet sheet = workbook.createSheet(sheetName);

        String[] columns = {
                "Month", "GSTIN", "Party Name", "Invoice Number",
                "Invoice Date", "Taxable Value", "IGST", "SGST", "CGST"
        };

        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }

        int rowNum = 1;
        for (InvoiceRecord r : data) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(r.getMonth());
            row.createCell(1).setCellValue(r.getGstin());
            row.createCell(2).setCellValue(r.getPartyName());
            row.createCell(3).setCellValue(r.getInvoiceNumber());
            row.createCell(4).setCellValue(r.getInvoiceDate());
            row.createCell(5).setCellValue(r.getTaxableValue());
            row.createCell(6).setCellValue(r.getIgst());
            row.createCell(7).setCellValue(r.getSgst());
            row.createCell(8).setCellValue(r.getCgst());
        }
    }

    // ================= MISMATCH SHEET =================
    private void writeMismatchSheet(Workbook workbook,
                                    List<InvoiceRecord> mismatches,
                                    Map<String, InvoiceRecord> tallyMap,
                                    CellStyle redStyle,
                                    double tolerance) {

        Sheet sheet = workbook.createSheet("Mismatch_Report");
        String[] columns = {
                "Month",
                "GSTIN",
                "Party Name",
                "Invoice Number",
                "Invoice Date",
                "GST Taxable Value",
                "Tally Taxable Value",
                "GST IGST",
                "Tally IGST",
                "GST CGST",
                "Tally CGST",
                "Status"
        };

        // Header
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }

        int rowNum = 1;

        for (InvoiceRecord gstRec : mismatches) {
            String key = GeneralUtility.normalizeKeyPart(gstRec.getGstin()) + "_" + GeneralUtility.normalizeKeyPart(gstRec.getInvoiceNumber());

            InvoiceRecord tallyRec = tallyMap.get(key);

            Row row = sheet.createRow(rowNum++);

            // ================= COMMON INFO =================
            row.createCell(0).setCellValue(gstRec.getMonth());
            row.createCell(1).setCellValue(gstRec.getGstin());
            row.createCell(2).setCellValue(gstRec.getPartyName());
            row.createCell(3).setCellValue(gstRec.getInvoiceNumber());
            row.createCell(4).setCellValue(gstRec.getInvoiceDate());

            StringBuilder status = new StringBuilder();
            String direction = "";

            // ================= TAXABLE VALUE =================

            Cell gstTv = row.createCell(5);
            gstTv.setCellValue(gstRec.getTaxableValue());

            Cell tallyTv = row.createCell(6);
            tallyTv.setCellValue(tallyRec.getTaxableValue());

            if (GeneralUtility.diff(gstRec.getTaxableValue(), tallyRec.getTaxableValue(), tolerance)) {
                gstTv.setCellStyle(redStyle);
                tallyTv.setCellStyle(redStyle);
                direction = GeneralUtility.getDirection(
                        gstRec.getTaxableValue(),
                        tallyRec.getTaxableValue());
                GeneralUtility.appendStatus(status, "Taxable Value");
            }

            // ================= IGST =================
            Cell gstIgst = row.createCell(7);
            gstIgst.setCellValue(gstRec.getIgst());

            Cell tallyIgst = row.createCell(8);
            tallyIgst.setCellValue(tallyRec.getIgst());

            if (GeneralUtility.diff(gstRec.getIgst(), tallyRec.getIgst(), tolerance)) {
                gstIgst.setCellStyle(redStyle);
                tallyIgst.setCellStyle(redStyle);
                if (direction.isEmpty()) {
                    direction = GeneralUtility.getDirection(gstRec.getIgst(), tallyRec.getIgst());
                }
                GeneralUtility.appendStatus(status, "IGST");
            }
            // ================= CGST =================
            Cell gstCgst = row.createCell(9);
            gstCgst.setCellValue(gstRec.getCgst());

            Cell tallyCgst = row.createCell(10);
            tallyCgst.setCellValue(tallyRec.getCgst());

            if (GeneralUtility.diff(gstRec.getCgst(), tallyRec.getCgst(), tolerance)) {
                gstCgst.setCellStyle(redStyle);
                tallyCgst.setCellStyle(redStyle);

                if (direction.isEmpty()) {
                    direction = GeneralUtility.getDirection(gstRec.getCgst(), tallyRec.getCgst());
                }

                GeneralUtility.appendStatus(status, "CGST");
            }
            // ================= STATUS =================
            row.createCell(11).setCellValue(direction + " (" + status + ")");
        }
    }

    // ================= FILTERED REPORT ONLY =================
    private String generateFilteredExcel(ReconciliationContext ctx,
                                         Set<String> unmatchedGSTINs) throws Exception {
        String fileName = "Filtered_Report_" + System.currentTimeMillis() + ".xlsx";
        String path = System.getProperty("java.io.tmpdir") + "/" + fileName;

        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try (FileOutputStream fos = new FileOutputStream(path)) {
            String[] columns = {
                    "Month", "GSTIN", "Party Name", "Invoice Number",
                    "Invoice Date", "Taxable Value", "IGST", "CGST", "SGST"
            };

            // ================= SHEET 1: Missing in Tally =================
            Sheet gstFilteredSheet = workbook.createSheet("Missing_In_Tally_Filtered");
            createHeader(gstFilteredSheet, columns);
            appendRowsBySource(gstFilteredSheet, ctx.gstMap.values(), unmatchedGSTINs);

            // ================= SHEET 2: Missing in GST =================
            Sheet tallyFilteredSheet = workbook.createSheet("Missing_In_GST_Filtered");
            createHeader(tallyFilteredSheet, columns);
            appendRowsBySource(tallyFilteredSheet, ctx.tallyMap.values(), unmatchedGSTINs);
            workbook.write(fos);
        } finally {
            workbook.dispose();
        }
        return fileName;
    }

    private void createHeader(Sheet sheet, String[] columns) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }
    }

    private void appendRowsBySource(Sheet sheet,
                                    Collection<InvoiceRecord> records,
                                    Set<String> includeKeys) {

        int rowNum = 1;
        for (InvoiceRecord r : records) {
            String key = GeneralUtility.normalizeKeyPart(r.getGstin())
                    + "_" + GeneralUtility.normalizeKeyPart(r.getInvoiceNumber());
            if (!includeKeys.contains(key)) continue;
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(r.getMonth());
            row.createCell(1).setCellValue(r.getGstin());
            row.createCell(2).setCellValue(r.getPartyName());
            row.createCell(3).setCellValue(r.getInvoiceNumber());
            row.createCell(4).setCellValue(r.getInvoiceDate());
            row.createCell(5).setCellValue(r.getTaxableValue());
            row.createCell(6).setCellValue(r.getIgst());
            row.createCell(7).setCellValue(r.getCgst());
            row.createCell(8).setCellValue(r.getSgst());
        }
    }

    // ================= LOAD FILE =================
    private Map<String, InvoiceRecord> loadFileToMap(String filePath) throws Exception {
        Map<String, InvoiceRecord> map = new HashMap<>(20000);
        DataFormatter formatter = new DataFormatter();
        try (InputStream is = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(is)
        ) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String gstin = formatter.formatCellValue(row.getCell(1)).trim().toUpperCase();
                String invoiceNo = formatter.formatCellValue(row.getCell(3)).trim().toUpperCase();
                String key = GeneralUtility.normalizeKeyPart(gstin) + "_" + GeneralUtility.normalizeKeyPart(invoiceNo);
                InvoiceRecord record = new InvoiceRecord(
                        formatter.formatCellValue(row.getCell(0)),
                        gstin,
                        formatter.formatCellValue(row.getCell(2)),
                        invoiceNo,
                        formatter.formatCellValue(row.getCell(4)),
                        GeneralUtility.getNumeric(row.getCell(5), evaluator),
                        GeneralUtility.getNumeric(row.getCell(6), evaluator),
                        GeneralUtility.getNumeric(row.getCell(7), evaluator),
                        GeneralUtility.getNumeric(row.getCell(8), evaluator)
                );
                map.put(key, record);
            }
        }
        return map;
    }

    private Map<String, Double> buildFilterFieldData(Map<String, InvoiceRecord> map, String field) {
        Map<String, Double> result = new HashMap<>();
        for (InvoiceRecord r : map.values()) {
            String gstin = GeneralUtility.normalizeKeyPart(r.getGstin());
            double value = switch (field) {
                case "taxableValue" -> r.getTaxableValue();
                case "igst" -> r.getIgst();
                case "cgst" -> r.getCgst();
                case "sgst" -> r.getSgst();
                default -> 0.0;
            };
            result.merge(gstin, value, Double::sum);
        }
        return result;
    }
}