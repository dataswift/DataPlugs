--liquibase formatted sql

--changeset dataplugFitbit:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('activity', 'User''s Fitbit activity list', 'sequence'),
  ('profile', 'User''s Fitbit profile information', 'snapshots')
  ON CONFLICT (name) DO NOTHING;
