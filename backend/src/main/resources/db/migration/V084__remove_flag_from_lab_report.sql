-- V083: Remove Flag column from Lab Report print template.
UPDATE print_templates 
SET content = REPLACE(content, 
  '<thead><tr><th>Test Name</th><th class="c">Result</th><th class="c">Unit</th><th class="c">Ref Range</th><th class="c">Flag</th></tr></thead>',
  '<thead><tr><th>Test Name</th><th class="c">Result</th><th class="c">Unit</th><th class="c">Ref Range</th></tr></thead>'
)
WHERE document_type = 'LAB';
