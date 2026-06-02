package com.hms.api.printtemplate.request;

import com.hms.domain.shared.model.PrintTemplate;
import lombok.Data;

@Data
public class PrintTemplateRequest {
    private String name;
    private String documentType;
    private String printMode;
    private String height;
    private String width;
    private String marginTop;
    private String marginBottom;
    private String marginLeft;
    private String marginRight;
    private String margin;
    private String pageSize;
    private String pugTemplate;
    private String content;
    private String defaultPrinter;
    private Boolean isDefault;

    public PrintTemplate toEntity(PrintTemplate t) {
        if (name         != null) t.setName(name);
        if (documentType != null) t.setDocumentType(documentType);
        if (printMode    != null) t.setPrintMode(printMode);
        if (height       != null) t.setHeight(height);
        if (width        != null) t.setWidth(width);
        if (marginTop    != null) t.setMarginTop(marginTop);
        if (marginBottom != null) t.setMarginBottom(marginBottom);
        if (marginLeft   != null) t.setMarginLeft(marginLeft);
        if (marginRight  != null) t.setMarginRight(marginRight);
        if (margin       != null) t.setMargin(margin);
        if (pageSize     != null) t.setPageSize(pageSize);
        if (pugTemplate  != null) t.setPugTemplate(pugTemplate);
        if (content      != null) t.setContent(content);
        if (defaultPrinter != null) t.setDefaultPrinter(defaultPrinter);
        if (isDefault    != null) t.setDefault(isDefault);
        return t;
    }
}
