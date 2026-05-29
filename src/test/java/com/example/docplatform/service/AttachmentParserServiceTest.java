package com.example.docplatform.service;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentParserServiceTest {

    private final AttachmentParserService service = new AttachmentParserService();

    @Test
    void parseCsv_returnsMapFromHeaderAndFirstDataRow() {
        byte[] csv = "region,salesTotal,period\nUS,50000,Q1-2026\n".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", csv);

        Map<String, Object> result = service.parse(file);

        assertThat(result).containsEntry("region", "US")
                          .containsEntry("salesTotal", "50000")
                          .containsEntry("period", "Q1-2026");
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
    void parseExcel_returnsMapFromHeaderAndFirstDataRow() throws Exception {
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
