package com.example.docplatform.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AttachmentParserService {

    public Map<String, Object> parse(MultipartFile file) {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        try {
            if (name.endsWith(".csv"))  return parseCsv(file);
            if (name.endsWith(".xlsx")) return parseExcel(file);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read file: " + e.getMessage());
        }
        throw new IllegalArgumentException("Unsupported file type. Use .csv or .xlsx");
    }

    private Map<String, Object> parseCsv(MultipartFile file) throws Exception {
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> rows = reader.readAll();
            if (rows.size() < 2) {
                throw new IllegalArgumentException("File must have a header row and at least one data row");
            }
            String[] headers = rows.get(0);
            String[] values  = rows.get(1);
            Map<String, Object> result = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                result.put(headers[i].trim(), i < values.length ? values[i].trim() : "");
            }
            return result;
        }
    }

    private Map<String, Object> parseExcel(MultipartFile file) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            Row dataRow   = sheet.getRow(1);
            if (headerRow == null || dataRow == null) {
                throw new IllegalArgumentException("File must have a header row and at least one data row");
            }
            Map<String, Object> result = new HashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell headerCell = headerRow.getCell(i);
                Cell dataCell   = dataRow.getCell(i);
                if (headerCell == null) continue;
                String key   = headerCell.getStringCellValue().trim();
                String value = dataCell != null ? dataCell.toString().trim() : "";
                result.put(key, value);
            }
            return result;
        }
    }
}
