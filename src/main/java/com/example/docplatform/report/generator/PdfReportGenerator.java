package com.example.docplatform.report.generator;

import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.enums.FileFormat;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.util.Map;

@Component
@RequiredArgsConstructor
public final class PdfReportGenerator implements ReportGenerator {

    @Qualifier("reportEngine")
    private final SpringTemplateEngine templateEngine;

    @Override
    public byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception {
        Context ctx = new Context();
        ctx.setVariables(params);
        String html = templateEngine.process(template.getThymeleafTemplate(), ctx);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }

    @Override
    public FileFormat supportedFormat() { return FileFormat.PDF; }
}
