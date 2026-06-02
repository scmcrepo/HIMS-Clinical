package com.hms.api.printtemplate.response;

import com.hms.domain.shared.model.PrintTemplate;
import lombok.Data;
import java.util.UUID;

@Data
public class PrintTemplateResponse {
    private UUID    id;
    private String  name;
    private String  documentType;
    private String  printMode;
    private String  height;
    private String  width;
    private String  marginTop;
    private String  marginBottom;
    private String  marginLeft;
    private String  marginRight;
    private String  margin;
    private String  pageSize;
    private String  pugTemplate;
    private String  content;
    private String  defaultPrinter;
    private boolean isDefault;
    private int     status;

    public static PrintTemplateResponse from(PrintTemplate t) {
        PrintTemplateResponse r = new PrintTemplateResponse();
        r.id             = t.getId();
        r.name           = t.getName();
        r.documentType   = t.getDocumentType();
        r.printMode      = t.getPrintMode() != null ? t.getPrintMode() : "HTML";
        r.height         = t.getHeight()   != null ? t.getHeight()   : "297mm";
        r.width          = t.getWidth()    != null ? t.getWidth()    : "210mm";
        r.marginTop      = t.getMarginTop()    != null ? t.getMarginTop()    : "10mm";
        r.marginBottom   = t.getMarginBottom() != null ? t.getMarginBottom() : "10mm";
        r.marginLeft     = t.getMarginLeft()   != null ? t.getMarginLeft()   : "10mm";
        r.marginRight    = t.getMarginRight()  != null ? t.getMarginRight()  : "10mm";
        r.margin         = t.getMargin();
        r.pageSize       = t.getPageSize() != null ? t.getPageSize() : "A4";
        r.pugTemplate    = t.getPugTemplate();
        r.content        = t.getContent();
        r.defaultPrinter = t.getDefaultPrinter();
        r.isDefault      = t.isDefault();
        r.status         = t.getStatus() != null ? t.getStatus().ordinal() : 1;
        return r;
    }
}
