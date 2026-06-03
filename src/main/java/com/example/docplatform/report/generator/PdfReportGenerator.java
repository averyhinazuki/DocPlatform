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
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public final class PdfReportGenerator implements ReportGenerator {

    // openhtmltopdf parses HTML as XHTML — void elements must be self-closed
    private static final Pattern VOID_TAGS = Pattern.compile(
        "<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)((?:\\s[^>]*)?)(?<!/)>",
        Pattern.CASE_INSENSITIVE
    );

    private static String toXhtml(String html) {
        return VOID_TAGS.matcher(html).replaceAll("<$1$2/>");
    }

    @Qualifier("reportEngine")
    private final SpringTemplateEngine templateEngine;

    @Override
    public byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception {
        Object override = params.get("__content");
        String html;
        if (override != null && !override.toString().isBlank()) {
            html = override.toString();
        } else {
            String templateContent = template.getThymeleafTemplate();
            if (templateContent == null || templateContent.isBlank()) {
                throw new IllegalStateException(
                    "Template '" + template.getName() + "' has no HTML content. " +
                    "Re-create the template via the Templates page to generate its HTML.");
            }
            Context ctx = new Context();
            ctx.setVariables(params);
            html = templateEngine.process(templateContent, ctx);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(toXhtml(html), null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }

    @Override
    public FileFormat supportedFormat() { return FileFormat.PDF; }
}
