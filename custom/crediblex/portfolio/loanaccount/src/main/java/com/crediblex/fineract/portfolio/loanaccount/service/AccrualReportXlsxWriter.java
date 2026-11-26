/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportData;
import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportRowData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Writes the loan Accrual Report workbook in the same layout as the frontend download from the Repayment Schedule tab:
 * sheet "Accrual Report", a numeric Index, every other cell as text, bold header / Total / Total Lifetime rows, blank
 * total cells omitted and the forecast note under the totals.
 */
@Component
public class AccrualReportXlsxWriter {

    static final String SHEET_NAME = "Accrual Report";
    static final String[] HEADERS = { "Index", "End of Month", "Opening Principal", "Closing Principal", "Interest Accrued (Actual)",
            "Interest Forecast" };
    static final String TOTAL_LABEL = "Total";
    static final String LIFETIME_LABEL = "Total Lifetime Interest (Actual + Forecast)";

    private static final int[] COLUMN_WIDTHS_WCH = { 8, 15, 18, 18, 24, 18 };
    // SheetJS writes a {wch: n} column as width n + 0.83203125 characters (its default 12pt Calibri padding).
    private static final int SHEETJS_WIDTH_PADDING_UNITS = 213;
    private static final short FONT_HEIGHT_POINTS = 12;
    private static final int ACTUAL_COLUMN = 4;
    private static final int FORECAST_COLUMN = 5;

    public static String fileName(final Long loanId, final LocalDate businessDate) {
        return "Accrual-Report-" + loanId + "-" + businessDate + ".xlsx";
    }

    public byte[] write(final AccrualReportData report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.getFontAt(0).setFontHeightInPoints(FONT_HEIGHT_POINTS);
            final CellStyle bold = boldStyle(workbook);
            final Sheet sheet = workbook.createSheet(SHEET_NAME);

            int rowIndex = 0;
            final Row header = sheet.createRow(rowIndex++);
            for (int i = 0; i < HEADERS.length; i++) {
                text(header, i, HEADERS[i], bold);
            }

            for (AccrualReportRowData data : report.rows()) {
                final Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(data.index());
                text(row, 1, data.endOfMonth().toString(), null);
                text(row, 2, data.openingPrincipal(), null);
                text(row, 3, data.closingPrincipal(), null);
                text(row, ACTUAL_COLUMN, data.interestAccruedActual(), null);
                text(row, FORECAST_COLUMN, data.interestForecast(), null);
            }

            final Row total = sheet.createRow(rowIndex++);
            text(total, 0, TOTAL_LABEL, bold);
            text(total, ACTUAL_COLUMN, report.interestAccruedActualTotal(), bold);
            if (report.forecastEnabled()) {
                text(total, FORECAST_COLUMN, report.interestForecastTotal(), bold);
            }

            final Row lifetime = sheet.createRow(rowIndex++);
            text(lifetime, 0, LIFETIME_LABEL, bold);
            text(lifetime, FORECAST_COLUMN, report.lifetimeInterestTotal(), bold);

            if (report.forecastEnabled()) {
                text(sheet.createRow(rowIndex), 0, AccrualReportCalculator.FORECAST_NOTE, null);
            }

            for (int i = 0; i < COLUMN_WIDTHS_WCH.length; i++) {
                // Fixed widths: autoSizeColumn needs AWT fonts, unavailable in headless Docker.
                sheet.setColumnWidth(i, COLUMN_WIDTHS_WCH[i] * 256 + SHEETJS_WIDTH_PADDING_UNITS);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Accrual Report workbook for loan " + report.loanId(), e);
        }
    }

    private static CellStyle boldStyle(final XSSFWorkbook workbook) {
        final Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints(FONT_HEIGHT_POINTS);
        final CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private static void text(final Row row, final int column, final String value, final CellStyle style) {
        final Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
}
