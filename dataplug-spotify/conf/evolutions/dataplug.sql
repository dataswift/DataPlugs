--liquibase formatted sql

--changeset dataplugSpotify:endpoints context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('profile', 'User''s Spotify profile information', 'snapshots'),
  ('feed', 'A feed of Spotify tracks played', 'sequence')
  ON CONFLICT (name) DO NOTHING;

--changeset dataplugSpotify:endpointsInsertUserPlaylists context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('playlists/user', 'User''s playlists on Spotify', 'sequence')
ON CONFLICT (name) DO NOTHING;

--changeset dataplugSpotify:endpointsInsertUserPlaylistTracks context:data

INSERT INTO dataplug_endpoint (name, description, details)
VALUES
  ('playlists/tracks', 'Tracks from different playlists', 'sequence')
ON CONFLICT (name) DO NOTHING;

