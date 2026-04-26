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

    public ReconciliationResultDto process(String tallyPath, String gstPath) throws Exception {

        // Pre-size maps for performance
        Map<String, InvoiceRecord> tallyMap = loadFileToMap(tallyPath, 20000);
        Map<String, InvoiceRecord> gstMap = loadFileToMap(gstPath, 20000);

        List<InvoiceRecord> missingInTally = new ArrayList<>(5000);
        List<InvoiceRecord> missingInGST = new ArrayList<>(5000);
        List<InvoiceRecord> mismatches = new ArrayList<>(5000);

        // GST → Tally
        for (Map.Entry<String, InvoiceRecord> entry : gstMap.entrySet()) {
            String key = entry.getKey();
            InvoiceRecord tallyRec = tallyMap.get(key);

            if (tallyRec == null) {
                missingInTally.add(entry.getValue());
            } else if (GeneralUtility.isMismatch(entry.getValue(), tallyRec)) {
                mismatches.add(entry.getValue());
            }
        }

        // Tally → GST
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
                tallyMap
        );

        ReconciliationResultDto result = new ReconciliationResultDto();
        result.missingInTally = missingInTally.size();
        result.missingInGST = missingInGST.size();
        result.mismatches = mismatches.size();
        result.fileName = fileName;

        return result;
    }

    // ================= LOAD FILE =================
    private Map<String, InvoiceRecord> loadFileToMap(String filePath, int initialCapacity) throws Exception {

        Map<String, InvoiceRecord> map = new HashMap<>(initialCapacity);
        DataFormatter formatter = new DataFormatter();

        try (InputStream is = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String gstin = formatter.formatCellValue(row.getCell(1)).trim().toUpperCase();
                String invoiceNo = formatter.formatCellValue(row.getCell(3)).trim().toUpperCase();

                String key = gstin + "_" + invoiceNo;

                InvoiceRecord record = new InvoiceRecord(
                        formatter.formatCellValue(row.getCell(0)),
                        gstin,
                        formatter.formatCellValue(row.getCell(2)),
                        invoiceNo,
                        formatter.formatCellValue(row.getCell(4)),
                        GeneralUtility.getNumeric(row.getCell(5)),
                        GeneralUtility.getNumeric(row.getCell(6)),
                        GeneralUtility.getNumeric(row.getCell(7)),
                        GeneralUtility.getNumeric(row.getCell(8))
                );

                map.put(key, record);
            }
        }

        return map;
    }

    // ================= FINAL REPORT =================
    private void writeFinalReport(String filePath,
                                  List<InvoiceRecord> missingInTally,
                                  List<InvoiceRecord> missingInGST,
                                  List<InvoiceRecord> mismatches,
                                  Map<String, InvoiceRecord> tallyMap) throws Exception {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.setCompressTempFiles(true);

            CellStyle redStyle = workbook.createCellStyle();
            redStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            writeNormalSheet(workbook, "Missing_In_Tally", missingInTally);
            writeNormalSheet(workbook, "Missing_In_GST", missingInGST);
            writeMismatchSheet(workbook, "Mismatch_Report", mismatches, tallyMap, redStyle);

            workbook.write(fos);
        } finally {
            workbook.dispose();
        }
    }

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
                                    String sheetName,
                                    List<InvoiceRecord> mismatches,
                                    Map<String, InvoiceRecord> tallyMap,
                                    CellStyle redStyle) {

        Sheet sheet = workbook.createSheet(sheetName);

        String[] columns = {
                "Month", "GSTIN", "Party Name", "Invoice Number", "Invoice Date",
                "Taxable Value", "IGST", "CGST", "Status"
        };

        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }

        int rowNum = 1;

        for (InvoiceRecord gstRec : mismatches) {

            String key = gstRec.getGstin() + "_" + gstRec.getInvoiceNumber();
            InvoiceRecord tallyRec = tallyMap.get(key);

            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(gstRec.getMonth());
            row.createCell(1).setCellValue(gstRec.getGstin());
            row.createCell(2).setCellValue(gstRec.getPartyName());
            row.createCell(3).setCellValue(gstRec.getInvoiceNumber());
            row.createCell(4).setCellValue(gstRec.getInvoiceDate());

            StringBuilder status = new StringBuilder();
            String direction = "";

            // Taxable
            Cell tv = row.createCell(5);
            tv.setCellValue(gstRec.getTaxableValue());
            if (GeneralUtility.diff(gstRec.getTaxableValue(), tallyRec.getTaxableValue())) {
                tv.setCellStyle(redStyle);
                direction = GeneralUtility.getDirection(gstRec.getTaxableValue(), tallyRec.getTaxableValue());
                GeneralUtility.appendStatus(status, "Tax. Value");
            }

            // IGST
            Cell igst = row.createCell(6);
            igst.setCellValue(gstRec.getIgst());
            if (GeneralUtility.diff(gstRec.getIgst(), tallyRec.getIgst())) {
                igst.setCellStyle(redStyle);
                if (direction.isEmpty()) {
                    direction = GeneralUtility.getDirection(gstRec.getIgst(), tallyRec.getIgst());
                }
                GeneralUtility.appendStatus(status, "IGST");
            }

            // CGST
            Cell cgst = row.createCell(7);
            cgst.setCellValue(gstRec.getCgst());
            if (GeneralUtility.diff(gstRec.getCgst(), tallyRec.getCgst())) {
                cgst.setCellStyle(redStyle);
                if (direction.isEmpty()) {
                    direction = GeneralUtility.getDirection(gstRec.getCgst(), tallyRec.getCgst());
                }
                GeneralUtility.appendStatus(status, "CGST");
            }

            row.createCell(8).setCellValue(direction + " (" + status + ")");
        }
    }
}