package com.hms.application.print;

import com.hms.api.printtemplate.response.PrintOutputResponse;
import java.util.Map;

public interface PrintService {
    PrintOutputResponse print(String templateType, Map<String, String> printParams);
}
