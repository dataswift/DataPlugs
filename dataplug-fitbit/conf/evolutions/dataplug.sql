--liquibase formatted sql

--changeset dataplugFitbit:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('activity/day/summary', 'Summary of user''s activity throughout the da', 'single record a day'),
  ('lifetime/stats', 'User''s Fitbit lifetime statistics', 'snapshots'),
  ('weight', 'Body weight and BMI measurement', 'single record a day'),
  ('sleep', 'Fitbit sleep records', 'sequence'),
  ('activity', 'User''s Fitbit activity list', 'sequence'),
  ('profile', 'User''s Fitbit profile information', 'snapshots')
  ON CONFLICT (name) DO NOTHING;

--changeset dataplugFitbit:updateExistingWeightPathParameters

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{pathParameters,endDate}'::TEXT[], to_char(CURRENT_DATE- INTEGER '1', '\"YYYY-MM-DD\"')::jsonb)
WHERE dataplug_user.dataplug_endpoint = 'weight';

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{pathParameters,baseDate}'::TEXT[], to_char(CURRENT_DATE - INTEGER '31', '\"YYYY-MM-DD\"')::jsonb)
WHERE dataplug_user.dataplug_endpoint = 'weight';

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{storageParameters,earliestSyncedDate}'::TEXT[], to_char(CURRENT_DATE - INTEGER '1', '\"YYYY-MM-DD\"')::jsonb)
WHERE dataplug_user.dataplug_endpoint = 'weight';

