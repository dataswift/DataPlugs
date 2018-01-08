--liquibase formatted sql

--changeset dataplugCalendar:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('accounts', 'Monzo Bank Accounts', 'snapshots'),
  ('transactions', 'Payment transactions', 'sequence')
  ON CONFLICT (name) DO NOTHING;
