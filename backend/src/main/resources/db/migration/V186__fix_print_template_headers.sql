-- Swap Rate and Qty column headers and add Net Amount column header in print templates for OP Bill
UPDATE print_templates
SET content = REPLACE(content, 
    E'<th style="width: 8%">S.No</th>\n        <th>Service / Item</th>\n        <th class="r" style="width: 15%">Rate (&#8377;)</th>\n        <th class="r" style="width: 10%">Qty</th>\n        <th class="r" style="width: 15%">Discount (&#8377;)</th>\n        <th class="r" style="width: 20%">Amount (&#8377;)</th>',
    E'<th style="width: 8%">S.No</th>\n        <th>Service / Item</th>\n        <th class="r" style="width: 10%">Qty</th>\n        <th class="r" style="width: 15%">Rate (&#8377;)</th>\n        <th class="r" style="width: 15%">Discount (&#8377;)</th>\n        <th class="r" style="width: 15%">Amount (&#8377;)</th>\n        <th class="r" style="width: 15%">Net Amount (&#8377;)</th>'
)
WHERE document_type = 'BILL';

-- Swap Rate and Qty column headers and add Net Amount column header in print templates for Consolidated IP Bill
UPDATE print_templates
SET content = REPLACE(content, 
    E'<th style="width: 8%">S.No</th>\n        <th>Service</th>\n        <th class="r" style="width: 15%">Rate (&#8377;)</th>\n        <th class="r" style="width: 10%">Qty</th>\n        <th class="r" style="width: 15%">Discount (&#8377;)</th>\n        <th class="r" style="width: 20%">Amount (&#8377;)</th>',
    E'<th style="width: 8%">S.No</th>\n        <th>Service</th>\n        <th class="r" style="width: 10%">Qty</th>\n        <th class="r" style="width: 15%">Rate (&#8377;)</th>\n        <th class="r" style="width: 15%">Discount (&#8377;)</th>\n        <th class="r" style="width: 15%">Amount (&#8377;)</th>\n        <th class="r" style="width: 15%">Net Amount (&#8377;)</th>'
)
WHERE document_type = 'IP_BILL_CONSOLIDATED';
