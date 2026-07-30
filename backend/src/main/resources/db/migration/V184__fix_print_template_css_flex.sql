-- Fix Chrome printing blank page bugs by removing flexbox on the main page wrapper
UPDATE print_templates
SET content = REPLACE(content, '.page{display:flex;flex-direction:column;min-height:100%}', '.page{display:block;min-height:100%}');
