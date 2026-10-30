package com.tejesh.timetable.service;

import com.tejesh.timetable.model.SlotType;
import com.tejesh.timetable.model.TimetableSlot;
import com.tejesh.timetable.model.User;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * <h2>ExportService – Multi-Format Timetable Exporter</h2>
 *
 * <p>Exports a user's generated timetable into:
 * <ol>
 *   <li><b>CSV</b> via {@link java.io.PrintWriter} with clean column separation and escaping.</li>
 *   <li><b>Excel (.xlsx)</b> via <b>Apache POI</b> with color-coded slot types, styled headers, and auto-sized columns.</li>
 *   <li><b>PDF</b> via <b>Apache PDFBox 3.x</b> featuring formatted table grids, alternating row fills, and automatic pagination.</li>
 * </ol>
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    // ══════════════════════════════════════════════════════════════════════════
    //  1. CSV EXPORT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Exports the timetable to a standard Comma-Separated Values (.csv) file.
     *
     * @param user       the owner of the timetable
     * @param slots      list of scheduled timetable slots
     * @param outputFile destination file (e.g., Timetable_Tejesh.csv)
     * @return the generated File
     * @throws IOException if writing fails
     */
    public File exportToCsv(User user, List<TimetableSlot> slots, File outputFile) throws IOException {
        log.info("ExportService: Exporting {} slots to CSV at '{}'", slots.size(), outputFile.getAbsolutePath());

        ensureParentDirectoryExists(outputFile);

        try (PrintWriter writer = new PrintWriter(
                new BufferedWriter(new FileWriter(outputFile, StandardCharsets.UTF_8)))) {

            // Header Row
            writer.println("Day,Start Time,End Time,Subject,Topic,Slot Type,Meeting Link");

            for (TimetableSlot slot : slots) {
                String day = escapeCsv(slot.getDayOfWeek());
                String start = escapeCsv(slot.getStartTime());
                String end = escapeCsv(slot.getEndTime());
                String subject = escapeCsv(slot.getSubject());
                String topic = escapeCsv(slot.getTopic());
                String slotType = escapeCsv(slot.getSlotType() != null ? slot.getSlotType().name() : "");
                String meetingLink = escapeCsv(slot.getMeetingLink());

                writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        day, start, end, subject, topic, slotType, meetingLink);
            }
        }

        log.info("ExportService: CSV export complete ({} bytes)", outputFile.length());
        return outputFile;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  2. EXCEL (.XLSX) EXPORT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Exports the timetable to a styled Excel workbook (.xlsx) using Apache POI.
     * Color-codes slots:
     * <ul>
     *   <li>STUDY: Light green background</li>
     *   <li>BREAK: Light gray / blue background</li>
     *   <li>ZOOM_MEETING: Light orange / coral background with bold text</li>
     * </ul>
     *
     * @param user       the owner of the timetable
     * @param slots      list of scheduled timetable slots
     * @param outputFile destination file (e.g., Timetable_Tejesh.xlsx)
     * @return the generated File
     * @throws IOException if writing fails
     */
    public File exportToExcel(User user, List<TimetableSlot> slots, File outputFile) throws IOException {
        log.info("ExportService: Exporting {} slots to Excel at '{}'", slots.size(), outputFile.getAbsolutePath());

        ensureParentDirectoryExists(outputFile);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Personalized Timetable");
            sheet.setDisplayGridlines(true);

            // ── Styles Setup ──────────────────────────────────────────────────
            // 1. Title Banner Style
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFont(titleFont);

            // 2. Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorders(headerStyle);

            // 3. Study Slot Style (Soft Green)
            CellStyle studyStyle = workbook.createCellStyle();
            studyStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            studyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorders(studyStyle);

            // 4. Break Slot Style (Soft Gray / Light Turquoise)
            CellStyle breakStyle = workbook.createCellStyle();
            Font breakFont = workbook.createFont();
            breakFont.setItalic(true);
            breakFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            breakStyle.setFont(breakFont);
            breakStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            breakStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorders(breakStyle);

            // 5. Zoom Meeting Style (Soft Orange / Peach)
            CellStyle zoomStyle = workbook.createCellStyle();
            Font zoomFont = workbook.createFont();
            zoomFont.setBold(true);
            zoomFont.setColor(IndexedColors.DARK_RED.getIndex());
            zoomStyle.setFont(zoomFont);
            zoomStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
            zoomStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorders(zoomStyle);

            // 6. Meal Style (Warm Amber / Orange)
            CellStyle mealStyle = workbook.createCellStyle();
            Font mealFont = workbook.createFont();
            mealFont.setBold(true);
            mealFont.setColor(IndexedColors.BROWN.getIndex());
            mealStyle.setFont(mealFont);
            mealStyle.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            mealStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorders(mealStyle);

            // ── Draw Title Row ────────────────────────────────────────────────
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Personalized Study Timetable - " + user.getUsername() +
                    " (" + user.getExamTarget() + ")");
            titleCell.setCellStyle(titleStyle);

            // ── Draw Table Header Row ─────────────────────────────────────────
            String[] headers = {"Day", "Start Time", "End Time", "Subject", "Topic", "Type", "Meeting Link"};
            Row headerRow = sheet.createRow(2);
            headerRow.setHeightInPoints(24);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Draw Data Rows ────────────────────────────────────────────────
            int rowIdx = 3;
            for (TimetableSlot slot : slots) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(20);

                CellStyle rowStyle = switch (slot.getSlotType() != null ? slot.getSlotType() : SlotType.STUDY) {
                    case STUDY -> studyStyle;
                    case BREAK -> breakStyle;
                    case ZOOM_MEETING -> zoomStyle;
                    case MEAL, LUNCH, SNACK, DINNER -> mealStyle;
                };

                createStyledCell(row, 0, nvl(slot.getDayOfWeek(), ""), rowStyle);
                createStyledCell(row, 1, nvl(slot.getStartTime(), ""), rowStyle);
                createStyledCell(row, 2, nvl(slot.getEndTime(), ""), rowStyle);
                createStyledCell(row, 3, nvl(slot.getSubject(), "—"), rowStyle);
                createStyledCell(row, 4, nvl(slot.getTopic(), "—"), rowStyle);
                createStyledCell(row, 5, slot.getSlotType() != null ? slot.getSlotType().name() : "", rowStyle);
                createStyledCell(row, 6, nvl(slot.getMeetingLink(), ""), rowStyle);
            }

            // ── Auto-size Columns with minimum width ──────────────────────────
            for (int col = 0; col < headers.length; col++) {
                sheet.autoSizeColumn(col);
                int currentWidth = sheet.getColumnWidth(col);
                sheet.setColumnWidth(col, Math.max(currentWidth + 1200, 3200));
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }
        }

        log.info("ExportService: Excel export complete ({} bytes)", outputFile.length());
        return outputFile;
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void createStyledCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  3. PDF EXPORT (APACHE PDFBOX 3.X)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Exports the timetable to a publication-quality PDF with formatted tables,
     * headers, alternating row colors, and automatic pagination.
     *
     * @param user       the owner of the timetable
     * @param slots      list of scheduled timetable slots
     * @param outputFile destination file (e.g., Timetable_Tejesh.pdf)
     * @return the generated File
     * @throws IOException if PDF generation fails
     */
    public File exportToPdf(User user, List<TimetableSlot> slots, File outputFile) throws IOException {
        log.info("ExportService: Exporting {} slots to PDF at '{}'", slots.size(), outputFile.getAbsolutePath());

        ensureParentDirectoryExists(outputFile);

        try (PDDocument doc = new PDDocument()) {

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            // Table geometry (A4 Portrait = 595.28 x 841.89 pt)
            float margin = 30f;
            float tableWidth = 535f;
            float[] colWidths = {75f, 75f, 115f, 185f, 85f}; // Day, Time, Subject, Topic, Type
            String[] colHeaders = {"Day", "Time", "Subject", "Topic", "Activity Type"};

            float rowHeight = 18f;
            float headerRowHeight = 22f;

            PDPage currentPage = new PDPage(PDRectangle.A4);
            doc.addPage(currentPage);
            PDPageContentStream contentStream = new PDPageContentStream(doc, currentPage);

            float yPos = 800f;

            // Draw Document Title on Page 1
            drawTitleBanner(contentStream, fontBold, fontRegular, user, margin, yPos);
            yPos -= 55f;

            // Draw Table Header on Page 1
            drawTableHeader(contentStream, fontBold, margin, yPos, headerRowHeight, colWidths, colHeaders);
            yPos -= headerRowHeight;

            boolean alternateRow = false;

            for (TimetableSlot slot : slots) {
                // If approaching bottom margin, create a new page
                if (yPos < 50f) {
                    contentStream.close();

                    currentPage = new PDPage(PDRectangle.A4);
                    doc.addPage(currentPage);
                    contentStream = new PDPageContentStream(doc, currentPage);

                    yPos = 800f;
                    // Redraw Table Header on the new page
                    drawTableHeader(contentStream, fontBold, margin, yPos, headerRowHeight, colWidths, colHeaders);
                    yPos -= headerRowHeight;
                    alternateRow = false;
                }

                // Prepare row cell values
                String day = sanitize(slot.getDayOfWeek());
                String time = sanitize(slot.getStartTime() + " - " + slot.getEndTime());
                String subject = sanitize(nvl(slot.getSubject(), "—"));
                String topic = sanitize(nvl(slot.getTopic(), "—"));
                if (slot.getSlotType() == SlotType.ZOOM_MEETING && slot.getMeetingLink() != null) {
                    topic += " (" + sanitize(slot.getMeetingLink()) + ")";
                }
                String slotType = slot.getSlotType() != null ? slot.getSlotType().name() : "";

                // Pick styling based on slot type
                Color rowBgColor = getSlotBgColor(slot.getSlotType(), alternateRow);
                PDType1Font rowFont = (slot.getSlotType() == SlotType.BREAK) ? fontOblique : fontRegular;

                // Draw Row Background Fill
                contentStream.setNonStrokingColor(rowBgColor);
                contentStream.addRect(margin, yPos - rowHeight, tableWidth, rowHeight);
                contentStream.fill();

                // Draw Row Gridlines (Borders)
                contentStream.setStrokingColor(new Color(200, 205, 210));
                contentStream.setLineWidth(0.5f);
                contentStream.addRect(margin, yPos - rowHeight, tableWidth, rowHeight);
                contentStream.stroke();

                // Draw Cell Separator Lines
                float xSep = margin;
                for (int i = 0; i < colWidths.length - 1; i++) {
                    xSep += colWidths[i];
                    contentStream.moveTo(xSep, yPos);
                    contentStream.lineTo(xSep, yPos - rowHeight);
                    contentStream.stroke();
                }

                // Render Text in Cells
                contentStream.setNonStrokingColor(Color.BLACK);
                String[] cellValues = {day, time, subject, topic, slotType};
                float xText = margin;

                for (int i = 0; i < cellValues.length; i++) {
                    float cellWidth = colWidths[i];
                    String cellText = truncateToFit(cellValues[i], cellWidth - 8f, rowFont, 8.5f);

                    contentStream.beginText();
                    contentStream.setFont(rowFont, 8.5f);
                    contentStream.newLineAtOffset(xText + 4f, yPos - 13f);
                    contentStream.showText(cellText);
                    contentStream.endText();

                    xText += cellWidth;
                }

                yPos -= rowHeight;
                alternateRow = !alternateRow;
            }

            contentStream.close();
            doc.save(outputFile);
        }

        log.info("ExportService: PDF export complete ({} bytes)", outputFile.length());
        return outputFile;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PDF Drawing Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void drawTitleBanner(PDPageContentStream cs, PDType1Font fontBold, PDType1Font fontRegular,
                                 User user, float margin, float yPos) throws IOException {
        // App Title
        cs.beginText();
        cs.setFont(fontBold, 15f);
        cs.setNonStrokingColor(new Color(26, 54, 138)); // Deep Navy
        cs.newLineAtOffset(margin, yPos);
        cs.showText("AI Personalized Timetable & Study Assistant");
        cs.endText();

        // Subtitle / User info
        cs.beginText();
        cs.setFont(fontRegular, 10f);
        cs.setNonStrokingColor(new Color(80, 80, 80));
        cs.newLineAtOffset(margin, yPos - 16f);
        String subtitle = "Student: " + sanitize(user.getUsername()) +
                "  |  Target Exam: " + sanitize(user.getExamTarget()) +
                "  |  Auto-Generated Schedule";
        cs.showText(subtitle);
        cs.endText();

        // Decorative horizontal rule
        cs.setStrokingColor(new Color(26, 54, 138));
        cs.setLineWidth(1.5f);
        cs.moveTo(margin, yPos - 25f);
        cs.lineTo(margin + 535f, yPos - 25f);
        cs.stroke();
    }

    private void drawTableHeader(PDPageContentStream cs, PDType1Font fontBold, float margin, float yPos,
                                 float headerHeight, float[] colWidths, String[] colHeaders) throws IOException {
        float tableWidth = 535f;

        // Header Background Fill (Royal Blue)
        cs.setNonStrokingColor(new Color(41, 105, 176));
        cs.addRect(margin, yPos - headerHeight, tableWidth, headerHeight);
        cs.fill();

        // Header Border
        cs.setStrokingColor(new Color(26, 54, 138));
        cs.setLineWidth(1f);
        cs.addRect(margin, yPos - headerHeight, tableWidth, headerHeight);
        cs.stroke();

        // Column Separator Lines & Text
        float x = margin;
        for (int i = 0; i < colHeaders.length; i++) {
            cs.beginText();
            cs.setFont(fontBold, 9.5f);
            cs.setNonStrokingColor(Color.WHITE);
            cs.newLineAtOffset(x + 5f, yPos - 15f);
            cs.showText(colHeaders[i]);
            cs.endText();

            x += colWidths[i];
            if (i < colHeaders.length - 1) {
                cs.setStrokingColor(new Color(200, 220, 255));
                cs.setLineWidth(0.5f);
                cs.moveTo(x, yPos);
                cs.lineTo(x, yPos - headerHeight);
                cs.stroke();
            }
        }
    }

    private Color getSlotBgColor(SlotType type, boolean alternate) {
        if (type == null) return alternate ? new Color(248, 250, 252) : Color.WHITE;

        return switch (type) {
            case STUDY -> alternate ? new Color(236, 253, 243) : new Color(242, 254, 247); // Subtle Green
            case BREAK -> new Color(241, 245, 249); // Subtle Slate Gray
            case ZOOM_MEETING -> new Color(254, 243, 235); // Subtle Warm Peach
            case MEAL, LUNCH, SNACK, DINNER -> new Color(255, 247, 237); // Warm Light Amber/Orange
        };
    }

    private String truncateToFit(String text, float maxPoints, PDType1Font font, float fontSize) {
        if (text == null) return "";
        try {
            float width = font.getStringWidth(text) / 1000f * fontSize;
            if (width <= maxPoints) return text;

            while (text.length() > 3) {
                text = text.substring(0, text.length() - 2);
                width = font.getStringWidth(text + "..") / 1000f * fontSize;
                if (width <= maxPoints) {
                    return text + "..";
                }
            }
            return text;
        } catch (Exception e) {
            return text.substring(0, Math.min(text.length(), 20));
        }
    }

    private String sanitize(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private void ensureParentDirectoryExists(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private String nvl(String val, String fallback) {
        return (val == null || val.isBlank()) ? fallback : val;
    }
}
