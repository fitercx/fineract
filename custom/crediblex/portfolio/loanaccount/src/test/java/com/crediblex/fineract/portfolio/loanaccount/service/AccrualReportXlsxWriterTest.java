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

import static org.assertj.core.api.Assertions.assertThat;

import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportData;
import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportRowData;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class AccrualReportXlsxWriterTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 11, 24);

    // Loan 21499 on UAT: the frontend download Accrual-Report-21499-2026-11-24.xlsx.
    private static final List<AccrualReportRowData> ROWS = List.of(
            new AccrualReportRowData(1, LocalDate.of(2026, 9, 30), "71000.00", "71000.00", "0.00", ""),
            new AccrualReportRowData(2, LocalDate.of(2026, 10, 31), "71000.00", "71000.00", "0.00", ""),
            new AccrualReportRowData(3, LocalDate.of(2026, 11, 23), "71000.00", "0.00", "2188.36", ""));

    private final AccrualReportXlsxWriter writer = new AccrualReportXlsxWriter();

    @Test
    void writesTheSameLayoutAsTheFrontendDownload() throws IOException {
        final AccrualReportData report = new AccrualReportData(21499L, BUSINESS_DATE, true, ROWS, "2188.36", "0.00", "2188.36");

        try (XSSFWorkbook workbook = read(writer.write(report))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            final XSSFSheet sheet = workbook.getSheet("Accrual Report");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isEqualTo(6);

            assertThat(texts(sheet.getRow(0))).containsExactly("Index", "End of Month", "Opening Principal", "Closing Principal",
                    "Interest Accrued (Actual)", "Interest Forecast");

            final Row first = sheet.getRow(1);
            assertThat(first.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(first.getCell(0).getNumericCellValue()).isEqualTo(1);
            for (int column = 1; column <= 5; column++) {
                assertThat(first.getCell(column).getCellType()).isEqualTo(CellType.STRING);
            }
            assertThat(texts(first).subList(1, 6)).containsExactly("2026-09-30", "71000.00", "71000.00", "0.00", "");
            assertThat(texts(sheet.getRow(3)).subList(1, 6)).containsExactly("2026-11-23", "71000.00", "0.00", "2188.36", "");

            final Row total = sheet.getRow(4);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(total.getCell(1)).isNull();
            assertThat(total.getCell(4).getStringCellValue()).isEqualTo("2188.36");
            assertThat(total.getCell(5).getStringCellValue()).isEqualTo("0.00");

            final Row lifetime = sheet.getRow(5);
            assertThat(lifetime.getCell(0).getStringCellValue()).isEqualTo("Total Lifetime Interest (Actual + Forecast)");
            assertThat(lifetime.getCell(4)).isNull();
            assertThat(lifetime.getCell(5).getStringCellValue()).isEqualTo("2188.36");

            final Row note = sheet.getRow(6);
            assertThat(note.getCell(0).getStringCellValue()).isEqualTo(AccrualReportCalculator.FORECAST_NOTE);
            assertThat(note.getLastCellNum()).isEqualTo((short) 1);

            assertThat(isBold(workbook, sheet.getRow(0))).isTrue();
            assertThat(isBold(workbook, first)).isFalse();
            assertThat(isBold(workbook, total)).isTrue();
            assertThat(isBold(workbook, lifetime)).isTrue();
            assertThat(isBold(workbook, note)).isFalse();

            final double[] widths = { 8.83203125, 15.83203125, 18.83203125, 18.83203125, 24.83203125, 18.83203125 };
            for (int column = 0; column < widths.length; column++) {
                assertThat(sheet.getColumnWidth(column) / 256.0).isEqualTo(widths[column]);
            }
        }
    }

    @Test
    void omitsTheForecastTotalAndTheNoteWhenForecastingIsDisabled() throws IOException {
        final AccrualReportData report = new AccrualReportData(7L, BUSINESS_DATE, false, ROWS, "2188.36", "", "2188.36");

        try (XSSFWorkbook workbook = read(writer.write(report))) {
            final XSSFSheet sheet = workbook.getSheet("Accrual Report");
            assertThat(sheet.getLastRowNum()).isEqualTo(5);
            assertThat(sheet.getRow(4).getCell(5)).isNull();
            assertThat(sheet.getRow(5).getCell(5).getStringCellValue()).isEqualTo("2188.36");
        }
    }

    @Test
    void namesTheFileLikeTheFrontendDownload() {
        assertThat(AccrualReportXlsxWriter.fileName(21499L, BUSINESS_DATE)).isEqualTo("Accrual-Report-21499-2026-11-24.xlsx");
    }

    private static XSSFWorkbook read(final byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static List<String> texts(final Row row) {
        return IntStream.range(0, row.getLastCellNum()).mapToObj(i -> {
            final Cell cell = row.getCell(i);
            return cell.getCellType() == CellType.NUMERIC ? String.valueOf((int) cell.getNumericCellValue()) : cell.getStringCellValue();
        }).toList();
    }

    private static boolean isBold(final XSSFWorkbook workbook, final Row row) {
        return workbook.getFontAt(row.getCell(0).getCellStyle().getFontIndex()).getBold();
    }
}
