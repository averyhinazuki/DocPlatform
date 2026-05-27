package com.example.docplatform.report.generator;

import com.example.docplatform.document.ReportTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportGeneratorTest {

    private ReportTemplate sampleTemplate() {
        ReportTemplate t = new ReportTemplate();
        t.setName("sales");
        t.setVariables(List.of("revenue", "orders"));
        t.setThymeleafTemplate("""
            <!DOCTYPE html><html><body>
            <p>Revenue: <span th:text="${revenue}">0</span></p>
            <p>Orders: <span th:text="${orders}">0</span></p>
            </body></html>
            """);
        return t;
    }

    @Test
    void csvGeneratorProducesHeaderAndRow() throws Exception {
        CsvReportGenerator gen = new CsvReportGenerator();
        byte[] csv = gen.generate(sampleTemplate(), Map.of("revenue", 5000, "orders", 42));
        String content = new String(csv);
        assertThat(content).contains("revenue").contains("orders").contains("5000").contains("42");
    }

    @Test
    void excelGeneratorProducesNonEmptyBytes() throws Exception {
        ExcelReportGenerator gen = new ExcelReportGenerator();
        byte[] xlsx = gen.generate(sampleTemplate(), Map.of("revenue", 5000, "orders", 42));
        assertThat(xlsx.length).isGreaterThan(0);
    }
}
