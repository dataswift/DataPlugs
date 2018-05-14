--liquibase formatted sql

--changeset dataplugFitbit:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('feed', 'User feed''s posts', 'sequence'),
  ('events', 'User''s events list', 'sequence'),
  ('profile', 'User''s Facebook profile information', 'snapshots')
  ON CONFLICT (name) DO NOTHING;

--changeset dataplugFacebook:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('profile/picture', 'User''s Facebook profile picture information', 'snapshots')
ON CONFLICT (name) DO NOTHING;