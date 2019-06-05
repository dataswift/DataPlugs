--liquibase formatted sql

--changeset dataplugUber:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('rides', 'User rides''s history', 'sequence'),
  ('profile', 'User''s uber profile information', 'snapshots'),
  ('places', 'User''s work place on Uber', 'snapshots')
  ON CONFLICT (name) DO NOTHING;

