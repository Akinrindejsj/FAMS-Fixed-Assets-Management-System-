package com.example.fams.reports;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

/** Excel and PDF export for any {@link TabularReport}. */
@Service
public class TabularExportService {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private String display(TabularReport.Column column, Object value) {
        if (value == null) return "";
        if ("money".equals(column.type()) && value instanceof BigDecimal b) return MONEY.format(b);
        return String.valueOf(value);
    }

    public byte[] toExcel(TabularReport report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String name = report.title().replaceAll("[:\\\\/?*\\[\\]]", " ");
            Sheet sheet = workbook.createSheet(name.length() > 31 ? name.substring(0, 31) : name);

            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(bold);
            DataFormat format = workbook.createDataFormat();
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(format.getFormat("#,##0.00"));
            CellStyle boldMoney = workbook.createCellStyle();
            boldMoney.setFont(bold);
            boldMoney.setDataFormat(format.getFormat("#,##0.00"));

            int rowIndex = 0;
            Cell title = sheet.createRow(rowIndex++).createCell(0);
            title.setCellValue(report.title());
            title.setCellStyle(boldStyle);
            sheet.createRow(rowIndex++).createCell(0).setCellValue("Generated: " + report.generatedDate());
            for (Map.Entry<String, Object> s : report.summary().entrySet()) {
                Row r = sheet.createRow(rowIndex++);
                r.createCell(0).setCellValue(s.getKey());
                Cell v = r.createCell(1);
                if (s.getValue() instanceof BigDecimal b) {
                    v.setCellValue(b.doubleValue());
                    v.setCellStyle(moneyStyle);
                } else {
                    v.setCellValue(String.valueOf(s.getValue()));
                }
            }
            rowIndex++;

            List<TabularReport.Column> columns = report.columns();
            Row header = sheet.createRow(rowIndex++);
            for (int i = 0; i < columns.size(); i++) {
                Cell c = header.createCell(i);
                c.setCellValue(columns.get(i).label());
                c.setCellStyle(boldStyle);
            }
            for (Map<String, Object> data : report.rows()) {
                writeRow(sheet.createRow(rowIndex++), columns, data, moneyStyle);
            }
            if (!report.totals().isEmpty()) {
                writeRow(sheet.createRow(rowIndex++), columns, report.totals(), boldMoney);
            }
            for (String note : report.notes()) {
                rowIndex++;
                sheet.createRow(rowIndex).createCell(0).setCellValue(note);
            }
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate Excel export", ex);
        }
    }

    private void writeRow(Row row, List<TabularReport.Column> columns, Map<String, Object> data, CellStyle moneyStyle) {
        for (int i = 0; i < columns.size(); i++) {
            Object value = data.get(columns.get(i).key());
            if (value == null) continue;
            Cell cell = row.createCell(i);
            if ("money".equals(columns.get(i).type()) && value instanceof BigDecimal b) {
                cell.setCellValue(b.doubleValue());
                cell.setCellStyle(moneyStyle);
            } else if ("number".equals(columns.get(i).type()) && value instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else {
                cell.setCellValue(String.valueOf(value));
            }
        }
    }

    public byte[] toPdf(TabularReport report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(out));
            Document document = new Document(pdf, PageSize.A4.rotate());
            document.add(new Paragraph(report.title()).setBold().setFontSize(16));
            document.add(new Paragraph("Generated: " + report.generatedDate()).setFontSize(9));
            for (Map.Entry<String, Object> s : report.summary().entrySet()) {
                Object v = s.getValue();
                document.add(new Paragraph(s.getKey() + ": " + (v instanceof BigDecimal b ? MONEY.format(b) : v)).setFontSize(9));
            }

            List<TabularReport.Column> columns = report.columns();
            Table table = new Table(UnitValue.createPercentArray(columns.size())).useAllAvailableWidth();
            for (TabularReport.Column c : columns) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(c.label()).setBold().setFontSize(7)));
            }
            if (report.rows().isEmpty()) {
                table.addCell(new com.itextpdf.layout.element.Cell(1, columns.size())
                        .add(new Paragraph("No records found for the selected criteria.").setFontSize(8))
                        .setTextAlignment(TextAlignment.CENTER));
            } else {
                for (Map<String, Object> data : report.rows()) {
                    addPdfRow(table, columns, data, false);
                }
                if (!report.totals().isEmpty()) {
                    addPdfRow(table, columns, report.totals(), true);
                }
            }
            document.add(table);
            for (String note : report.notes()) {
                document.add(new Paragraph(note).setFontSize(8));
            }
            document.close();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate PDF export", ex);
        }
    }

    private void addPdfRow(Table table, List<TabularReport.Column> columns, Map<String, Object> data, boolean bold) {
        for (TabularReport.Column c : columns) {
            Paragraph p = new Paragraph(display(c, data.get(c.key()))).setFontSize(7);
            if (bold) p.setBold();
            com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell().add(p);
            if ("money".equals(c.type()) || "number".equals(c.type())) cell.setTextAlignment(TextAlignment.RIGHT);
            table.addCell(cell);
        }
    }
}
