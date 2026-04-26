package org.tally.gst_reconcillation.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.tally.gst_reconcillation.dto.ReconciliationResultDto;
import org.tally.gst_reconcillation.model.InvoiceRecord;
import org.tally.gst_reconcillation.util.GeneralUtility;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReconciliationService {

    public ReconciliationResultDto process(String tallyPath, String gstPath) throws Exception {

        Map<String, InvoiceRecord> tallyMap = loadFileToMap(tallyPath);
        Map<String, InvoiceRecord> gstMap   = loadFileToMap(gstPath);

        List<InvoiceRecord> missingInTally = new ArrayList<>();
        List<InvoiceRecord> missingInGST   = new ArrayList<>();
        List<InvoiceRecord> mismatches     = new ArrayList<>();

        for (String key : gstMap.keySet()) {
            if (!tallyMap.containsKey(key)) {
                missingInTally.add(gstMap.get(key));
            } else {
                if (GeneralUtility.isMismatch(gstMap.get(key), tallyMap.get(key))) {
                    mismatches.add(gstMap.get(key));
                }
            }
        }

        for (String key : tallyMap.keySet()) {
            if (!gstMap.containsKey(key)) {
                missingInGST.add(tallyMap.get(key));
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
    private static Map<String, InvoiceRecord> loadFileToMap(String filePath) throws Exception {

        Map<String, InvoiceRecord> map = new HashMap<>();
        Workbook workbook = new XSSFWorkbook(new FileInputStream(filePath));
        Sheet sheet = workbook.getSheetAt(0);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String gstin = GeneralUtility.getCellValue(row.getCell(1)).trim().toUpperCase();
            String invoiceNo = GeneralUtility.getCellValue(row.getCell(3)).trim().toUpperCase();

            String key = gstin + "_" + invoiceNo;

            InvoiceRecord record = new InvoiceRecord(
                    GeneralUtility.getCellValue(row.getCell(0)),
                    gstin,
                    GeneralUtility.getCellValue(row.getCell(2)),
                    invoiceNo,
                    GeneralUtility.getCellValue(row.getCell(4)),
                    GeneralUtility.getNumeric(row.getCell(5)),
                    GeneralUtility.getNumeric(row.getCell(6)),
                    GeneralUtility.getNumeric(row.getCell(7)),
                    GeneralUtility.getNumeric(row.getCell(8))
            );
            map.put(key, record);
        }
        workbook.close();
        return map;
    }

    // ================= FINAL REPORT =================
    private static void writeFinalReport(String fileName,
                                         List<InvoiceRecord> missingInTally,
                                         List<InvoiceRecord> missingInGST,
                                         List<InvoiceRecord> mismatches,
                                         Map<String, InvoiceRecord> tallyMap) throws Exception {

        Workbook workbook = new XSSFWorkbook();

        // Red highlight style
        CellStyle redStyle = workbook.createCellStyle();
        redStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Sheets
        writeNormalSheet(workbook, "Missing_In_Tally", missingInTally);
        writeNormalSheet(workbook, "Missing_In_GST", missingInGST);
        writeMismatchSheet(workbook, "Mismatch_Report", mismatches, tallyMap, redStyle);

        FileOutputStream fos = new FileOutputStream(fileName);
        workbook.write(fos);
        workbook.close();
        fos.close();
    }

    // ================= NORMAL SHEETS =================
    private static void writeNormalSheet(Workbook workbook,
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
    private static void writeMismatchSheet(Workbook workbook,
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
            StringBuilder statusFields = new StringBuilder();
            String direction = "";

            // Taxable Value
            Cell tvCell = row.createCell(5);
            tvCell.setCellValue(gstRec.getTaxableValue());

            if (GeneralUtility.diff(gstRec.getTaxableValue(), tallyRec.getTaxableValue())) {
                tvCell.setCellStyle(redStyle);
                direction = GeneralUtility.getDirection(gstRec.getTaxableValue(), tallyRec.getTaxableValue());
                GeneralUtility.appendStatus(statusFields, "Tax. Value");
            }

            // IGST
            Cell igstCell = row.createCell(6);
            igstCell.setCellValue(gstRec.getIgst());

            if (GeneralUtility.diff(gstRec.getIgst(), tallyRec.getIgst())) {
                igstCell.setCellStyle(redStyle);
                if (direction.isEmpty()) {
                    direction = GeneralUtility.getDirection(gstRec.getIgst(), tallyRec.getIgst());
                }
                GeneralUtility.appendStatus(statusFields, "IGST");
            }

            // CGST
            Cell cgstCell = row.createCell(7);
            cgstCell.setCellValue(gstRec.getCgst());

            if (GeneralUtility.diff(gstRec.getCgst(), tallyRec.getCgst())) {
                cgstCell.setCellStyle(redStyle);
                if (direction.isEmpty()) {
                    direction = GeneralUtility.getDirection(gstRec.getCgst(), tallyRec.getCgst());
                }
                GeneralUtility.appendStatus(statusFields, "CGST");
            }

            String finalStatus = direction + " (" + statusFields.toString() + ")";
            row.createCell(8).setCellValue(finalStatus);
        }
    }
}