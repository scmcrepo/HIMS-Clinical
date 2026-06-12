-- V099__rename_ip_automated_orders.sql
UPDATE features 
SET feature_key = 'PRESCRIBED_ORDERS', 
    description = 'Prescribed Orders' 
WHERE feature_key = 'IP_AUTOMATED_ORDERS';
