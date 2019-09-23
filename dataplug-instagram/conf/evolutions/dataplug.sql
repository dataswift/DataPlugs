--liquibase formatted sql

--changeset dataplugInstagram:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('feed', 'User feed''s media', 'sequence'),
  ('profile', 'User''s Instagram profile information', 'snapshots')
  ON CONFLICT (name) DO NOTHING;
