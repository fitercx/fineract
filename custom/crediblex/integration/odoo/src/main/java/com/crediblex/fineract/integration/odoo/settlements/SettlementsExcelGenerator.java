/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.integration.odoo.settlements;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class SettlementsExcelGenerator {

    private static final String[] HEADERS = { "Client Name", "Product Name", "Event", "Loan ID", "Savings Account ID", "Amount" };

    public byte[] generate(final List<SettlementsReportRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final Sheet sheet = workbook.createSheet("Settlements");
            final Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }

            int rowIdx = 1;
            for (final SettlementsReportRow row : rows) {
                final Row excelRow = sheet.createRow(rowIdx++);
                excelRow.createCell(0).setCellValue(nullToEmpty(row.getClientName()));
                excelRow.createCell(1).setCellValue(nullToEmpty(row.getProductName()));
                excelRow.createCell(2).setCellValue(nullToEmpty(row.getEvent()));
                if (row.getLoanId() != null) {
                    excelRow.createCell(3).setCellValue(row.getLoanId());
                } else {
                    excelRow.createCell(3).setCellValue("");
                }
                if (row.getSavingsAccountId() != null) {
                    excelRow.createCell(4).setCellValue(row.getSavingsAccountId());
                } else {
                    excelRow.createCell(4).setCellValue("");
                }
                if (row.getAmount() != null) {
                    excelRow.createCell(5).setCellValue(row.getAmount().doubleValue());
                } else {
                    excelRow.createCell(5).setCellValue("");
                }
            }

            for (int i = 0; i < HEADERS.length; i++) {
                // Fixed widths — autoSizeColumn needs AWT fonts unavailable in headless Docker
                sheet.setColumnWidth(i, 20 * 256);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Settlements Report Excel", e);
        }
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }
}
