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

    public static String getCellValue(Cell cell) {
        return (cell == null) ? "" : cell.toString().trim();
    }

    public static double getNumeric(Cell cell) {
        if (cell == null) return 0;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        try {
            return Double.parseDouble(cell.toString().replace(",", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public static String normalizeKeyPart(String value) {
        if (value == null) return "";

        // Remove all non-alphanumeric characters
        return value.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase()
                .trim();
    }
}
