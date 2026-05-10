package org.tally.gst_reconcillation.util;

import org.apache.poi.ss.usermodel.*;
import org.tally.gst_reconcillation.model.InvoiceRecord;

public class GeneralUtility {
    public static boolean isMismatch(InvoiceRecord a, InvoiceRecord b, double tolerance) {
        return diff(a.getTaxableValue(), b.getTaxableValue(), tolerance)
                || diff(a.getIgst(), b.getIgst(), tolerance)
                || diff(a.getCgst(), b.getCgst(), tolerance);
    }

    public static boolean diff(double a, double b, double tolerance) {
        return Math.abs(a - b) > tolerance;
    }

    public static String getDirection(double gstVal, double tallyVal) {
        return (gstVal > tallyVal) ? "Less in Tally" : "More in Tally";
    }

    public static void appendStatus(StringBuilder sb, String field) {
        if (!sb.isEmpty()) sb.append("|");
        sb.append(field);
    }

    public static double getNumeric(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return 0;
        }
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                CellValue evaluated = evaluator.evaluate(cell);
                if (evaluated != null &&
                        evaluated.getCellType() == CellType.NUMERIC) {
                    return evaluated.getNumberValue();
                }
                return 0;
            }
            if (type == CellType.NUMERIC) {
                return cell.getNumericCellValue();
            }
            if (type == CellType.STRING) {
                String value = cell.getStringCellValue().replace(",", "").trim();
                if (value.isEmpty()) {
                    return 0;
                }
                return Double.parseDouble(value);
            }
        } catch (Exception e) {
            System.out.println("Failed parsing cell: " + cell);
        }
        return 0;
    }

    public static String normalizeKeyPart(String value) {
        if (value == null) return "";
        // Remove all non-alphanumeric characters
        return value.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase()
                .trim();
    }
}
