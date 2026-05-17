package org.tally.gst_reconcillation.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.tally.gst_reconcillation.dto.ReconciliationResultDto;
import org.tally.gst_reconcillation.model.InvoiceRecord;
import org.tally.gst_reconcillation.util.GeneralUtility;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;

@Service
public class ReconciliationService {

    public ReconciliationResultDto process(String tallyFile, String gstFile, String aggregateField, double tolerance) throws Exception {

        Map<String, List<InvoiceRecord>> tallyMap = loadFileToMap(tallyFile);
        Map<String, List<InvoiceRecord>> gstMap = loadFileToMap(gstFile);

        // STEP 1: Build GSTIN-level aggregate sums for the selected field
        // and collect GSTINs where both sides match → exclude entirely
        Set<String> excludedGSTINs = resolveExcludedGSTINs(tallyMap, gstMap, aggregateField, tolerance);

        // STEP 2: Classify records
        List<InvoiceRecord> missingInTally = new ArrayList<>();
        List<InvoiceRecord> missingInGST = new ArrayList<>();
        List<MismatchRow> mismatches = new ArrayList<>();

        Set<String> allKeys = new HashSet<>();
        for (String key : gstMap.keySet()) {
            if (!excludedGSTINs.contains(extractGstin(key))) allKeys.add(key);
        }
        for (String key : tallyMap.keySet()) {
            if (!excludedGSTINs.contains(extractGstin(key))) allKeys.add(key);
        }

        for (String key : allKeys) {
            List<InvoiceRecord> gstRecords = gstMap.getOrDefault(key, Collections.emptyList());
            List<InvoiceRecord> tallyRecords = tallyMap.getOrDefault(key, Collections.emptyList());

            if (gstRecords.isEmpty()) {
                missingInGST.addAll(tallyRecords);
                continue;
            }
            if (tallyRecords.isEmpty()) {
                missingInTally.addAll(gstRecords);
                continue;
            }
            List<InvoiceRecord> unmatchedTally = new ArrayList<>(tallyRecords);

            for (InvoiceRecord gstRec : gstRecords) {
                InvoiceRecord exactMatch = findExactMatch(gstRec, unmatchedTally, tolerance);
                if (exactMatch != null) {
                    unmatchedTally.remove(exactMatch);
                } else {
                    InvoiceRecord tallyPair = unmatchedTally.isEmpty()
                            ? tallyRecords.get(0)
                            : unmatchedTally.get(0);

                    if (!unmatchedTally.isEmpty()) unmatchedTally.remove(tallyPair);

                    mismatches.add(new MismatchRow(gstRec, tallyPair));
                }
            }

            // Any leftover tally records with no GST counterpart → Missing in GST
            missingInGST.addAll(unmatchedTally);
        }

        // STEP 3: Write report
        String fileName = "Reconciliation_Report_" + System.currentTimeMillis() + ".xlsx";
        String outputPath = System.getProperty("java.io.tmpdir") + "/" + fileName;
        writeFinalReport(outputPath, missingInTally, missingInGST, mismatches, tolerance);

        ReconciliationResultDto dto = new ReconciliationResultDto();
        dto.fileName = fileName;
        dto.missingInTally = missingInTally.size();
        dto.missingInGST = missingInGST.size();
        dto.mismatches = mismatches.size();
        return dto;
    }


    /**
     * Aggregates the selected field per GSTIN from both maps.
     * If the aggregate matches for a GSTIN, that GSTIN is excluded from all sheets.
     */
    private Set<String> resolveExcludedGSTINs(Map<String, List<InvoiceRecord>> tallyMap,
                                               Map<String, List<InvoiceRecord>> gstMap,
                                               String field, double tolerance) {
        Map<String, Double> tallyTotals = buildGstinAggregates(tallyMap, field);
        Map<String, Double> gstTotals = buildGstinAggregates(gstMap, field);

        Set<String> excluded = new HashSet<>();
        Set<String> allGSTINs = new HashSet<>();
        allGSTINs.addAll(tallyTotals.keySet());
        allGSTINs.addAll(gstTotals.keySet());

        for (String gstin : allGSTINs) {
            double tallyTotal = tallyTotals.getOrDefault(gstin, 0.0);
            double gstTotal = gstTotals.getOrDefault(gstin, 0.0);
            if (Math.abs(tallyTotal - gstTotal) < tolerance) {
                excluded.add(gstin);
            }
        }
        return excluded;
    }

    private Map<String, Double> buildGstinAggregates(Map<String, List<InvoiceRecord>> map,
                                                      String field) {
        Map<String, Double> totals = new HashMap<>();
        for (List<InvoiceRecord> records : map.values()) {
            for (InvoiceRecord r : records) {
                String gstin = GeneralUtility.normalizeKeyPart(r.getGstin());
                totals.merge(gstin, GeneralUtility.getFieldValue(r, field), Double::sum);
            }
        }
        return totals;
    }

    /**
     * Finds a record in the tally list that exactly matches the GST record
     * (all four fields within tolerance). Returns null if none found.
     */
    private InvoiceRecord findExactMatch(InvoiceRecord gstRec,
                                         List<InvoiceRecord> tallyList,
                                         double tolerance) {
        for (InvoiceRecord t : tallyList) {
            if (!GeneralUtility.isMismatch(gstRec, t, tolerance)) {
                return t;
            }
        }
        return null;
    }

    private void writeFinalReport(String filePath,
                                  List<InvoiceRecord> missingInTally,
                                  List<InvoiceRecord> missingInGST,
                                  List<MismatchRow> mismatches,
                                  double tolerance) throws Exception {

        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            CellStyle redStyle = buildRedStyle(workbook);
            CellStyle headerStyle = buildHeaderStyle(workbook);

            writeNormalSheet(workbook, "Missing_In_Tally", missingInTally, headerStyle);
            writeNormalSheet(workbook, "Missing_In_GST", missingInGST, headerStyle);
            writeMismatchSheet(workbook, mismatches, redStyle, headerStyle, tolerance);

            workbook.write(fos);
        } finally {
            workbook.dispose();
        }
    }

    private void writeNormalSheet(Workbook workbook,
                                  String sheetName,
                                  List<InvoiceRecord> data,
                                  CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(sheetName);
        String[] columns = {
                "Month", "GSTIN", "Party Name", "Invoice Number",
                "Invoice Date", "Taxable Value", "IGST", "SGST", "CGST"
        };

        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
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

    private void writeMismatchSheet(Workbook workbook,
                                    List<MismatchRow> mismatches,
                                    CellStyle redStyle,
                                    CellStyle headerStyle,
                                    double tolerance) {
        Sheet sheet = workbook.createSheet("Mismatch_Report");
        String[] columns = {
                "Month", "GSTIN", "Party Name", "Invoice Number", "Invoice Date",
                "GST Taxable Value", "Tally Taxable Value",
                "GST IGST", "Tally IGST",
                "GST SGST", "Tally SGST",
                "GST CGST", "Tally CGST",
                "Status"
        };

        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (MismatchRow mr : mismatches) {
            InvoiceRecord g = mr.gst();
            InvoiceRecord t = mr.tally();
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(g.getMonth());
            row.createCell(1).setCellValue(g.getGstin());
            row.createCell(2).setCellValue(g.getPartyName());
            row.createCell(3).setCellValue(g.getInvoiceNumber());
            row.createCell(4).setCellValue(g.getInvoiceDate());

            StringBuilder status = new StringBuilder();
            String direction = "";

            // Taxable Value
            Cell gstTv = row.createCell(5);
            gstTv.setCellValue(g.getTaxableValue());
            Cell tallyTv = row.createCell(6);
            tallyTv.setCellValue(t.getTaxableValue());
            if (GeneralUtility.diff(g.getTaxableValue(), t.getTaxableValue(), tolerance)) {
                gstTv.setCellStyle(redStyle);
                tallyTv.setCellStyle(redStyle);
                direction = GeneralUtility.getDirection(g.getTaxableValue(), t.getTaxableValue());
                GeneralUtility.appendStatus(status, "Taxable Value");
            }

            // IGST
            Cell gstIgst = row.createCell(7);
            gstIgst.setCellValue(g.getIgst());
            Cell tallyIgst = row.createCell(8);
            tallyIgst.setCellValue(t.getIgst());
            if (GeneralUtility.diff(g.getIgst(), t.getIgst(), tolerance)) {
                gstIgst.setCellStyle(redStyle);
                tallyIgst.setCellStyle(redStyle);
                if (direction.isEmpty()) direction = GeneralUtility.getDirection(g.getIgst(), t.getIgst());
                GeneralUtility.appendStatus(status, "IGST");
            }

            // SGST
            Cell gstSgst = row.createCell(9);
            gstSgst.setCellValue(g.getSgst());
            Cell tallySgst = row.createCell(10);
            tallySgst.setCellValue(t.getSgst());
            if (GeneralUtility.diff(g.getSgst(), t.getSgst(), tolerance)) {
                gstSgst.setCellStyle(redStyle);
                tallySgst.setCellStyle(redStyle);
                if (direction.isEmpty()) direction = GeneralUtility.getDirection(g.getSgst(), t.getSgst());
                GeneralUtility.appendStatus(status, "SGST");
            }

            // CGST
            Cell gstCgst = row.createCell(11);
            gstCgst.setCellValue(g.getCgst());
            Cell tallyCgst = row.createCell(12);
            tallyCgst.setCellValue(t.getCgst());
            if (GeneralUtility.diff(g.getCgst(), t.getCgst(), tolerance)) {
                gstCgst.setCellStyle(redStyle);
                tallyCgst.setCellStyle(redStyle);
                if (direction.isEmpty()) direction = GeneralUtility.getDirection(g.getCgst(), t.getCgst());
                GeneralUtility.appendStatus(status, "CGST");
            }

            row.createCell(13).setCellValue(direction + " (" + status + ")");
        }
    }

    private Map<String, List<InvoiceRecord>> loadFileToMap(String filePath) throws Exception {
        Map<String, List<InvoiceRecord>> map = new HashMap<>(20000);
        DataFormatter formatter = new DataFormatter();

        try (InputStream is = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(is)) {

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String gstin = formatter.formatCellValue(row.getCell(1)).trim().toUpperCase();
                String invoiceNo = formatter.formatCellValue(row.getCell(3)).trim().toUpperCase();

                if (gstin.isEmpty() && invoiceNo.isEmpty()) continue;

                String key = GeneralUtility.normalizeKeyPart(gstin)
                        + "_" + GeneralUtility.normalizeKeyPart(invoiceNo);

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

                map.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
            }
        }
        return map;
    }

    private String extractGstin(String key) {
        int idx = key.indexOf('_');
        return idx == -1 ? key : key.substring(0, idx);
    }

    private CellStyle buildRedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private record MismatchRow(InvoiceRecord gst, InvoiceRecord tally) {}
}
