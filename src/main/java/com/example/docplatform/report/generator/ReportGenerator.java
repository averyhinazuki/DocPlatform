package com.example.docplatform.report.generator;

import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.enums.FileFormat;

import java.util.Map;

public sealed interface ReportGenerator
    permits PdfReportGenerator, ExcelReportGenerator, CsvReportGenerator {

    byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception;
    FileFormat supportedFormat();
}
