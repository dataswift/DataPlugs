--liquibase formatted sql

--changeset dataplugCalendar:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details) VALUES ('google/events', 'Google Calendars', null)
  ON CONFLICT (name) DO NOTHING;

--changeset dataplugCalendarNames:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details) VALUES ('google/calendars', 'Users Google Calendars', null)
  ON CONFLICT (name) DO NOTHING;
