--liquibase formatted sql

--changeset dataplugFitbit:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('feed', 'User feed''s posts', 'sequence'),
  ('events', 'User''s events list', 'sequence'),
  ('profile', 'User''s Facebook profile information', 'snapshots')
  ON CONFLICT (name) DO NOTHING;
