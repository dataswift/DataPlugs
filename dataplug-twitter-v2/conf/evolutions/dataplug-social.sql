--liquibase formatted sql

--changeset dataplugSocial:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details) VALUES ('followers', 'Twitter Followers', null)
  ON CONFLICT (name) DO NOTHING;

INSERT INTO dataplug_endpoint (name, description, details) VALUES ('friends', 'Twitter Friends', null)
  ON CONFLICT (name) DO NOTHING;

INSERT INTO dataplug_endpoint (name, description, details) VALUES ('tweets', 'Twitter Tweets', null)
  ON CONFLICT (name) DO NOTHING;
