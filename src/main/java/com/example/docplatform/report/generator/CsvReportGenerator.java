package com.example.docplatform.report.generator;

import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.enums.FileFormat;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public final class CsvReportGenerator implements ReportGenerator {

    @Override
    @SuppressWarnings("unchecked")
    public byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception {
        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            List<String> columns = template.getVariables();
            writer.writeNext(columns.toArray(String[]::new));

            Object rowsObj = params.get("rows");
            if (rowsObj instanceof List<?> rawRows && !rawRows.isEmpty()) {
                for (Object rowObj : rawRows) {
                    if (rowObj instanceof Map<?, ?> rowMap) {
                        String[] row = columns.stream()
                            .map(c -> { Object v = rowMap.get(c); return v != null ? v.toString() : ""; })
                            .toArray(String[]::new);
                        writer.writeNext(row);
                    }
                }
            } else {
                String[] row = columns.stream()
                    .map(c -> params.getOrDefault(c, "").toString())
                    .toArray(String[]::new);
                writer.writeNext(row);
            }
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public FileFormat supportedFormat() { return FileFormat.CSV; }
}
