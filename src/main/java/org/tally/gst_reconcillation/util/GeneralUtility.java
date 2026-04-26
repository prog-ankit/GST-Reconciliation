package org.tally.gst_reconcillation.util;

import org.apache.poi.ss.usermodel.*;
import org.tally.gst_reconcillation.model.InvoiceRecord;

public class GeneralUtility {
    public static boolean isMismatch(InvoiceRecord a, InvoiceRecord b) {
        return diff(a.getTaxableValue(), b.getTaxableValue())
                || diff(a.getIgst(), b.getIgst())
                || diff(a.getCgst(), b.getCgst());
    }

    public static boolean diff(double a, double b) {
        return Math.abs(a - b) > 0.01;
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
}
