package com.example.docplatform.report.generator;

import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.enums.FileFormat;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public final class ExcelReportGenerator implements ReportGenerator {

    @Override
    public byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Report");

            Row header = sheet.createRow(0);
            List<String> columns = template.getVariables();
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i));
            }

            Row data = sheet.createRow(1);
            for (int i = 0; i < columns.size(); i++) {
                Object val = params.get(columns.get(i));
                data.createCell(i).setCellValue(val != null ? val.toString() : "");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Override
    public FileFormat supportedFormat() { return FileFormat.EXCEL; }
}
