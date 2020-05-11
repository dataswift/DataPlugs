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

--changeset dataplugFacebook:updateExistingDataplugUserRecords

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters,fields}', '"id,admin_creator,application,call_to_action,caption,created_time,description,feed_targeting,from,icon,is_hidden,is_published,link,message,message_tags,name,object_id,picture,place,privacy,properties,shares,source,status_type,story,targeting,to,type,updated_time,full_picture"')
WHERE dataplug_user.dataplug_endpoint = 'feed';

--changeset dataplugFacebook:updateExistingDataplugUserRecords2

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters,fields}', '"id,birthday,email,first_name,gender,hometown,is_verified,last_name,locale,name,political,relationship_status,religion,quotes,significant_other,third_party_id,timezone,updated_time,verified,website,friends,age_range,link"')
WHERE dataplug_user.dataplug_endpoint = 'profile';

--changeset dataplugFacebook:endpointsInsertUserLikes context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('likes/pages', 'User''s likes on Facebook', 'sequence')
ON CONFLICT (name) DO NOTHING;

--changeset dataplugFacebook:updateExistingDataplugUserRecordsDueToRecentDeprecations

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters,fields}', '"id,attachments,caption,created_time,description,from,full_picture,icon,is_instagram_eligible,link,message,message_tags,name,object_id,permalink_url,place,shares,status_type,type,updated_time,with_tags"')
WHERE dataplug_user.dataplug_endpoint = 'feed';

--changeset dataplugFacebook:updateExistingDataplugUserRecordsDueToRecentDeprecations2

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters,fields}', '"id,first_name,last_name,middle_name,name,link,age_range,birthday,email,languages,public_key,relationship_status,religion,significant_other,sports,friends"')
WHERE dataplug_user.dataplug_endpoint = 'profile';

--changeset dataplugFacebook:addMoreProfileFieldsDueToRequirements

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters}', '{"fields":"id,first_name,last_name,middle_name,name,link,age_range,email,languages,name_format,public_key,relationship_status,religion,significant_other,sports,friends","summary":"total_count"}')
WHERE dataplug_user.dataplug_endpoint = 'profile';

--changeset dataplugFacebook:updateConfigurationToV5

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{url}', '"https://graph.facebook.com/v5.0"')
WHERE dataplug_user.endpoint_configuration -> 'url' = '"https://graph.facebook.com/v2.10"'

--changeset dataplugFacebook:endpointsInsertUserPosts context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('posts', 'User''s own Facebook posts', 'sequence')
ON CONFLICT (name) DO NOTHING;

--changeset dataplugFacebook:addMoreIsSphericalToPosts

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters}', '{"fields":"id,attachments,caption,created_time,description,from,full_picture,icon,link,is_instagram_eligible,is_spherical,message,message_tags,name,object_id,permalink_url,place,shares,status_type,type,updated_time,with_tags","limit":"250"}')
WHERE dataplug_user.dataplug_endpoint = 'posts';

--changeset dataplugFacebook:changeLimitToFeed

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters,limit}', '"250"')
WHERE dataplug_user.dataplug_endpoint = 'feed';

--changeset dataplugFacebook:resetFeedEndpoint

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters}', '{"fields":"id,attachments,caption,created_time,description,from,full_picture,icon,link,is_instagram_eligible,message,message_tags,name,object_id,permalink_url,place,shares,status_type,type,updated_time,with_tags","limit":"250"}')
WHERE dataplug_user.phata = (SELECT phata FROM dataplug_user AS u WHERE u.dataplug_endpoint = 'posts' AND dataplug_user.phata = u.phata) AND dataplug_user.dataplug_endpoint = 'feed';

--changeset dataplugFacebook:removeUserLikesEndpoint context:data

ALTER TABLE log_dataplug_user DROP CONSTRAINT log_dataplug_user_dataplug_endpoint_fkey;
DELETE FROM dataplug_user_status WHERE dataplug_endpoint = 'likes/pages';
DELETE FROM dataplug_user WHERE dataplug_endpoint = 'likes/pages';
DELETE FROM dataplug_endpoint WHERE name = 'likes/pages';

--changeset dataplugFacebook:changeLimitToFeed2

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters,limit}', '"100"')
WHERE dataplug_user.dataplug_endpoint = 'feed';

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters,limit}', '"100"')
WHERE dataplug_user.dataplug_endpoint = 'posts';

--changeset dataplugFacebook:endpointsInsertUserLikes2 context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('likes/pages', 'User''s likes on Facebook', 'sequence')
ON CONFLICT (name) DO NOTHING;

--changeset dataplugFacebook:addBirthdayAndLocationToProfile

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters}', '{"fields":"id,first_name,last_name,middle_name,name,link,age_range,email,languages,name_format,public_key,relationship_status,religion,significant_other,sports,friends,location,birthday","summary":"total_count"}')
WHERE dataplug_user.dataplug_endpoint = 'profile';

--changeset dataplugFacebook:updateUserLikesFields

UPDATE dataplug_user
SET endpoint_configuration = jsonb_set(endpoint_configuration, '{queryParameters,fields}', '"id,about,created_time,awards,can_checkin,can_post,category,category_list,checkins,description,description_html,display_subtext,emails,fan_count,has_whatsapp_number,link,location,name,overall_star_rating,place_type,rating_count,username,verification_status,website,whatsapp_number"')
WHERE dataplug_user.dataplug_endpoint = 'likes/pages';


