-- Fix HK stock identifiers for Stooq provider
-- Previous format was 0001.hk, new format is 1.HK
UPDATE market.security_identifier
SET identifier = UPPER(LTRIM(SUBSTR(identifier, 1, LENGTH(identifier) - 3), '0')) || '.HK'
WHERE provider = 'stooq' AND identifier LIKE '%.hk';

-- Fix HSI index identifier
UPDATE market.security_identifier
SET identifier = '^HSI'
WHERE provider = 'stooq' AND identifier = '^hsi';

-- Fix HSTECH index identifier (just in case)
UPDATE market.security_identifier
SET identifier = '^HSTECH'
WHERE provider = 'stooq' AND identifier = '^hstech';
