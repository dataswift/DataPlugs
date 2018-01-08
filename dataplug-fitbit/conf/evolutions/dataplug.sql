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
