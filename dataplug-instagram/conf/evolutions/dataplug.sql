--liquibase formatted sql

--changeset dataplugInstagram:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('feed', 'User feed''s media', 'sequence'),
  ('profile', 'User''s Instagram profile information', 'snapshots')
  ON CONFLICT (name) DO NOTHING;

--changeset dataplugInstagram:backUpEndpoints context:data

CREATE TABLE dataplug_user_old AS SELECT * FROM dataplug_user;

--changeset dataplugInstagram:deleteEndpoints context:data

DELETE FROM user_link;
DELETE FROM user_oauth2_info WHERE provider_id = 'instagram';
DELETE FROM user_user;

DELETE FROM dataplug_user WHERE dataplug_endpoint = 'feed';
DELETE FROM dataplug_user WHERE dataplug_endpoint = 'profile';

