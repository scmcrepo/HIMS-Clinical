package com.hms.api.printtemplate.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Response from GET /print
 *
 * HTML mode:       printData = compiled HTML string
 * DOT_MATRIX mode: rawPages  = List of ESC/POS strings, one per page
 */
@Data
@Builder
public class PrintOutputResponse {
    private String       printMode;
    private String       printData;
    private List<String> rawPages;
    private String       width;
    private String       height;
    private String       marginTop;
    private String       marginBottom;
    private String       marginLeft;
    private String       marginRight;
    private String       defaultPrinter;
    private List<String> serversidePrinters;
}
