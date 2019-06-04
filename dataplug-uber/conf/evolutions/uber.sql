--liquibase formatted sql

--changeset dataplugUber:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('history', 'User rides''s history', 'sequence'),
  ('uberprofile', 'User''s uber profile information', 'snapshots')
  ON CONFLICT (name) DO NOTHING;

