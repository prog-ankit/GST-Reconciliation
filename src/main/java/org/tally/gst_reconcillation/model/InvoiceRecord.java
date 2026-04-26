package org.tally.gst_reconcillation.model;

public class InvoiceRecord {
    private String month;
    private String gstin;
    private String partyName;
    private String invoiceNumber;
    private String invoiceDate;
    private double taxableValue;
    private double igst;
    private double sgst;
    private double cgst;

    public InvoiceRecord(String month, String gstin, String partyName,
                         String invoiceNumber, String invoiceDate,
                         double taxableValue, double igst,
                         double sgst, double cgst) {
        this.month = month;
        this.gstin = gstin;
        this.partyName = partyName;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.taxableValue = taxableValue;
        this.igst = igst;
        this.sgst = sgst;
        this.cgst = cgst;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public void setPartyName(String partyName) {
        this.partyName = partyName;
    }

    public void setInvoiceDate(String invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public void setTaxableValue(double taxableValue) {
        this.taxableValue = taxableValue;
    }

    public void setIgst(double igst) {
        this.igst = igst;
    }

    public void setSgst(double sgst) {
        this.sgst = sgst;
    }

    public void setCgst(double cgst) {
        this.cgst = cgst;
    }

    public String getGstin() {
        return gstin;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getPartyName() {
        return partyName;
    }

    public String getInvoiceDate() {
        return invoiceDate;
    }

    public double getTaxableValue() {
        return taxableValue;
    }

    public double getIgst() {
        return igst;
    }

    public double getSgst() {
        return sgst;
    }

    public double getCgst() {
        return cgst;
    }
}
