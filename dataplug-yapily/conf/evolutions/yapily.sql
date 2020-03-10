--liquibase formatted sql

--changeset dataplugYapily:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('accounts', 'User rides''s history', 'sequence'),
  ('transactions', 'User''s uber profile information', 'sequence'),
  ('identity', 'User''s work place on Uber', 'snapshots')
  ON CONFLICT (name) DO NOTHING;
