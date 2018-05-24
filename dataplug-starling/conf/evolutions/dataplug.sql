--liquibase formatted sql

--changeset dataplugSpotify:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('profile', 'User''s Spotify profile information', 'snapshots'),
  ('feed', 'A feed of Spotify tracks played', 'sequence')
  ON CONFLICT (name) DO NOTHING;
