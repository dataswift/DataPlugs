--liquibase formatted sql

--changeset dataplugSpotify:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('profile', 'Individual user''s Starling profile information', 'snapshots'),
  ('transactions', 'A list of transactions associated with the account holder', 'sequence')
  ON CONFLICT (name) DO NOTHING;
