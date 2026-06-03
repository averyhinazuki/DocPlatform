package com.example.docplatform.service;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentParserServiceTest {

    private final AttachmentParserService service = new AttachmentParserService();

    @Test
    void parseCsv_singleRow_returnsTopLevelKeysAndRowsList() {
        byte[] csv = "region,salesTotal,period\nUS,50000,Q1-2026\n".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", csv);

        Map<String, Object> result = service.parse(file);

        assertThat(result).containsEntry("region", "US")
                          .containsEntry("salesTotal", "50000")
                          .containsEntry("period", "Q1-2026");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("region", "US");
    }

    @Test
    void parseCsv_multipleRows_allRowsInList() {
        byte[] csv = "dept,users\ndep1,10\ndep2,20\ndep3,30\n".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", csv);

        Map<String, Object> result = service.parse(file);

        assertThat(result).containsEntry("dept", "dep1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)).containsEntry("dept", "dep2").containsEntry("users", "20");
        assertThat(rows.get(2)).containsEntry("dept", "dep3").containsEntry("users", "30");
    }

    @Test
    void parseCsv_throwsWhenLessThanTwoRows() {
        byte[] csv = "region,salesTotal\n".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", csv);

        assertThatThrownBy(() -> service.parse(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("header row");
    }

    @Test
    void parseExcel_singleRow_returnsTopLevelKeysAndRowsList() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        XSSFRow header = sheet.createRow(0);
        header.createCell(0).setCellValue("region");
        header.createCell(1).setCellValue("salesTotal");
        XSSFRow data = sheet.createRow(1);
        data.createCell(0).setCellValue("EU");
        data.createCell(1).setCellValue("75000");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();

        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        Map<String, Object> result = service.parse(file);

        assertThat(result).containsEntry("region", "EU")
                          .containsEntry("salesTotal", "75000");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(1);
    }

    @Test
    void parseExcel_multipleRows_allRowsInList() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        XSSFRow header = sheet.createRow(0);
        header.createCell(0).setCellValue("dept");
        header.createCell(1).setCellValue("month");
        for (int i = 1; i <= 4; i++) {
            XSSFRow row = sheet.createRow(i);
            row.createCell(0).setCellValue("dep" + i);
            row.createCell(1).setCellValue(i);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();

        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        Map<String, Object> result = service.parse(file);

        assertThat(result).containsEntry("dept", "dep1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(4);
        assertThat(rows.get(3)).containsEntry("dept", "dep4").containsEntry("month", "4");
    }

    @Test
    void parseExcel_numericIntegerCell_noDecimalSuffix() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        XSSFRow header = sheet.createRow(0);
        header.createCell(0).setCellValue("month");
        XSSFRow data = sheet.createRow(1);
        data.createCell(0).setCellValue(1.0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();

        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        Map<String, Object> result = service.parse(file);

        assertThat(result).containsEntry("month", "1");
    }

    @Test
    void parseExcel_throwsWhenMissingDataRow() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        XSSFRow header = sheet.createRow(0);
        header.createCell(0).setCellValue("region");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();

        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        assertThatThrownBy(() -> service.parse(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("header row");
    }

    @Test
    void parse_throwsForUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "data.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.parse(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported file type");
    }
}
