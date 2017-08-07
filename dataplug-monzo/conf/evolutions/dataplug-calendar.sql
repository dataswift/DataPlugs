--liquibase formatted sql

--changeset dataplugCalendar:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details) VALUES ('monzo', 'Monzo Bank', null)
  ON CONFLICT (name) DO NOTHING;
