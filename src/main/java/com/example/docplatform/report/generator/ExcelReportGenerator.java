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
    @SuppressWarnings("unchecked")
    public byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Report");
            List<String> columns = template.getVariables();

            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i));
            }

            Object rowsObj = params.get("rows");
            if (rowsObj instanceof List<?> rawRows && !rawRows.isEmpty()) {
                int rowIdx = 1;
                for (Object rowObj : rawRows) {
                    if (rowObj instanceof Map<?, ?> rowMap) {
                        Row dataRow = sheet.createRow(rowIdx++);
                        for (int i = 0; i < columns.size(); i++) {
                            Object val = rowMap.get(columns.get(i));
                            dataRow.createCell(i).setCellValue(val != null ? val.toString() : "");
                        }
                    }
                }
            } else {
                Row data = sheet.createRow(1);
                for (int i = 0; i < columns.size(); i++) {
                    Object val = params.get(columns.get(i));
                    data.createCell(i).setCellValue(val != null ? val.toString() : "");
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Override
    public FileFormat supportedFormat() { return FileFormat.EXCEL; }
}
