--liquibase formatted sql

--changeset dataplugFitbit:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details) VALUES ('dailyActivitySummary', 'Summary of user''s daily activities', null)
  ON CONFLICT (name) DO NOTHING;
