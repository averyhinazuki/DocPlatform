package com.example.docplatform.service;

import com.opencsv.CSVReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
            List<String[]> rawRows = reader.readAll();
            if (rawRows.size() < 2) {
                throw new IllegalArgumentException("File must have a header row and at least one data row");
            }
            String[] headers = rawRows.get(0);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int r = 1; r < rawRows.size(); r++) {
                String[] values = rawRows.get(r);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i].trim(), i < values.length ? values[i].trim() : "");
                }
                rows.add(row);
            }

            Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
            result.put("rows", rows);
            return result;
        }
    }

    private Map<String, Object> parseExcel(MultipartFile file) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("File must have a header row and at least one data row");
            }

            int numCols = headerRow.getLastCellNum();
            String[] headers = new String[numCols];
            for (int i = 0; i < numCols; i++) {
                Cell c = headerRow.getCell(i);
                headers[i] = c != null ? c.getStringCellValue().trim() : "";
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, Object> rowMap = new LinkedHashMap<>();
                for (int i = 0; i < numCols; i++) {
                    if (headers[i].isEmpty()) continue;
                    Cell cell = row.getCell(i);
                    rowMap.put(headers[i], cell != null ? cellValue(cell) : "");
                }
                rows.add(rowMap);
            }

            if (rows.isEmpty()) {
                throw new IllegalArgumentException("File must have a header row and at least one data row");
            }

            Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
            result.put("rows", rows);
            return result;
        }
    }

    private String cellValue(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        return cell.toString().trim();
    }
}
