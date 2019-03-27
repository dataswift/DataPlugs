--liquibase formatted sql

--changeset dataplug:generate_endpoint_statuses_from_logs context:data

INSERT INTO dataplug_user_status (phata, dataplug_endpoint, endpoint_configuration, endpoint_variant, created, successful, message)
SELECT s.phata, s.dataplug_endpoint, s.endpoint_configuration, s.endpoint_variant, s.created, s.successful, s.message
FROM (
       SELECT phata, dataplug_endpoint, endpoint_variant, MAX(created) AS MaxDate
       FROM log_dataplug_user
       GROUP BY phata, dataplug_endpoint, endpoint_variant
     ) m
       INNER JOIN log_dataplug_user s
                  ON m.phata = s.phata
                    AND m.dataplug_endpoint = s.dataplug_endpoint
                    AND m.endpoint_variant = s.endpoint_variant
                    AND m.MaxDate = s.created;

--rollback TRUNCATE TABLE dataplug_user_status;
