--liquibase formatted sql

--changeset dataplug:extensions
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

--changeset dataplug:log context:structures

CREATE SEQUENCE dataplug_endpoint_seq START WITH 1;

CREATE TABLE dataplug_endpoint (
  name        VARCHAR PRIMARY KEY,
  description VARCHAR NOT NULL,
  details     VARCHAR
);

CREATE SEQUENCE dataplug_user_link_seq START WITH 1;

CREATE TABLE dataplug_user (
  id                           INT8      NOT NULL DEFAULT nextval('dataplug_user_link_seq') PRIMARY KEY,
  phata                        VARCHAR   NOT NULL,
  dataplug_endpoint            VARCHAR   NOT NULL REFERENCES dataplug_endpoint (name),
  endpoint_configuration       JSONB,
  endpoint_variant             VARCHAR,
  endpoint_variant_description VARCHAR,
  active                       BOOLEAN   NOT NULL,
  created                      TIMESTAMP NOT NULL DEFAULT now(),
  CONSTRAINT dataplug_user_link_unique UNIQUE (phata, dataplug_endpoint, endpoint_variant)
);

CREATE SEQUENCE log_dataplug_seq_id START WITH 1;

CREATE TABLE log_dataplug_user (
  id                     INT8      NOT NULL DEFAULT nextval('log_dataplug_seq_id') PRIMARY KEY,
  phata                  VARCHAR   NOT NULL,
  dataplug_endpoint      VARCHAR   NOT NULL REFERENCES dataplug_endpoint (name),
  endpoint_configuration JSONB     NOT NULL,
  endpoint_variant       VARCHAR,
  created                TIMESTAMP NOT NULL DEFAULT now(),
  successful             BOOLEAN   NOT NULL,
  message                VARCHAR
);

CREATE TABLE shared_notables (
  id           VARCHAR   NOT NULL PRIMARY KEY,
  created_time TIMESTAMP NOT NULL DEFAULT now(),
  phata        VARCHAR   NOT NULL,
  posted       BOOLEAN   NOT NULL,
  posted_time  TIMESTAMP,
  provider_id  VARCHAR,
  deleted      BOOLEAN   NOT NULL,
  deleted_time TIMESTAMP
);

--rollback DROP TABLE log_dataplug_user;
--rollback DROP SEQUENCE log_dataplug_seq_id;
--rollback DROP TABLE dataplug_user;
--rollback DROP SEQUENCE dataplug_user_link_seq;
--rollback DROP TABLE dataplug_endpoint;
--rollback DROP SEQUENCE dataplug_endpoint_seq;
--rollback DROP TABLE shared_notables;
