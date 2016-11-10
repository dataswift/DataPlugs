--liquibase formatted sql

--changeset notables:extensions
--typically must be superuser to create extensions
--CREATE EXTENSION "uuid-ossp";
--CREATE EXTENSION "pgcrypto";

--changeset dataplug:credentials context:structures

CREATE TABLE user_user (
  provider_id VARCHAR   NOT NULL,
  user_id     VARCHAR   NOT NULL,
  created     TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (provider_id, user_id)
);

CREATE TABLE user_oauth1_info (
  provider_id VARCHAR   NOT NULL,
  user_id     VARCHAR   NOT NULL,
  token       VARCHAR   NOT NULL,
  secret      VARCHAR   NOT NULL,
  created     TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (provider_id, user_id),
  FOREIGN KEY (provider_id, user_id) REFERENCES user_user (provider_id, user_id)
);

CREATE TABLE user_oauth2_info (
  provider_id   VARCHAR   NOT NULL,
  user_id       VARCHAR   NOT NULL,
  access_token  VARCHAR   NOT NULL,
  token_type    VARCHAR,
  expires_in    INT4,
  refresh_token VARCHAR,
  params        JSONB,
  created       TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (provider_id, user_id),
  FOREIGN KEY (provider_id, user_id) REFERENCES user_user (provider_id, user_id)
);

CREATE SEQUENCE user_link_seq START WITH 1;
CREATE TABLE user_link (
  link_id            INT8      NOT NULL DEFAULT nextval('user_link_seq') PRIMARY KEY,
  master_provider_id VARCHAR   NOT NULL,
  master_user_id     VARCHAR   NOT NULL,
  linked_provider_id VARCHAR   NOT NULL,
  linked_user_id     VARCHAR   NOT NULL,
  created            TIMESTAMP NOT NULL DEFAULT now(),
  FOREIGN KEY (master_provider_id, master_user_id) REFERENCES user_user (provider_id, user_id),
  FOREIGN KEY (linked_provider_id, linked_user_id) REFERENCES user_user (provider_id, user_id),
  CONSTRAINT user_link_unique UNIQUE (master_provider_id, master_user_id, linked_provider_id, linked_user_id)
);

CREATE OR REPLACE VIEW user_linked_user AS
  SELECT * FROM user_user;

--rollback DROP TABLE user_link;
--rollback DROP TABLE user_oauth2_info;
--rollback DROP TABLE user_oauth1_info;
--rollback DROP TABLE user_user;
