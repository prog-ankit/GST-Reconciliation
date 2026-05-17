package org.tally.gst_reconcillation.util;

import org.apache.poi.ss.usermodel.*;
import org.tally.gst_reconcillation.model.InvoiceRecord;

public class GeneralUtility {

    public static boolean isMismatch(InvoiceRecord gst, InvoiceRecord tally, double tolerance) {
        return diff(gst.getTaxableValue(), tally.getTaxableValue(), tolerance)
                || diff(gst.getIgst(), tally.getIgst(), tolerance)
                || diff(gst.getSgst(), tally.getSgst(), tolerance)
                || diff(gst.getCgst(), tally.getCgst(), tolerance);
    }

    public static boolean diff(double a, double b, double tolerance) {
        return Math.abs(a - b) > tolerance;
    }

    public static String getDirection(double gstVal, double tallyVal) {
        return (gstVal > tallyVal) ? "Less in Tally" : "More in Tally";
    }

    public static void appendStatus(StringBuilder sb, String field) {
        if (!sb.isEmpty()) sb.append(" | ");
        sb.append(field);
    }

    public static double getFieldValue(InvoiceRecord r, String field) {
        return switch (field) {
            case "taxableValue" -> r.getTaxableValue();
            case "igst" -> r.getIgst();
            case "sgst" -> r.getSgst();
            case "cgst" -> r.getCgst();
            default -> 0.0;
        };
    }

    public static double getNumeric(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return 0;
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                CellValue evaluated = evaluator.evaluate(cell);
                if (evaluated != null && evaluated.getCellType() == CellType.NUMERIC) {
                    return evaluated.getNumberValue();
                }
                return 0;
            }
            if (type == CellType.NUMERIC) return cell.getNumericCellValue();
            if (type == CellType.STRING) {
                String value = cell.getStringCellValue().replace(",", "").trim();
                return value.isEmpty() ? 0 : Double.parseDouble(value);
            }
        } catch (Exception e) {
            System.out.println("Failed parsing cell: " + cell);
        }
        return 0;
    }

    public static String normalizeKeyPart(String value) {
        if (value == null) return "";
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase().trim();
    }
}
