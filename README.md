# HAT DataPlugs

HAT Data Plugs written in Scala/Play

DataPlugs in the HAT ecosystem are self-contained services that act 
between a service providing data and a HAT. This project provides 
reusable libraries and structures for building such DataPlugs as well as
examples of already built ones.

The proposed implementation is built on the Scala/Play framework and
has a few internal dependencies:

- `commonPlay` as a set of various reusable blocks of code and libraries
- `hat-client-scala-play` as a Scala wrapper around the HAT HTTP API
- `marketsquare-client-scala-plau` as a Scala wrapper around the HATDeX MarketSquare HTTP API
- `dataplug` the core components of any DataPlug

## DataPlug design

TBD

Takes care of:
1. HAT Login
2. Social login (via "Silhouette") with common OAuth1/OAuth2 implementations provided and customisable
3. Source Endpoint API subscription management
4. Synchronisation scheduling and management using Akka Actors

## How to build a new DataPlug

TBD

1. Implement API endpoint interfaces (e.g. `GoogleCalendarInfo`)
2. Strongly suggested but optional - implement Strongly-typed classes describing API data structures
3. Tweak the way mapping is done between those structures and the HAT
4. Override any implementations of existing UI views
5. Tie everything together using DependencyInjection
6. Provide application configuration and social network (data source) API credentials

You will also need to extend project build settings to include the new plug, please see `/project/Build.scala` for an example

## How to run a DataPlug

Configure your environment variables with:

- `APPLICATION_SECRET` - application secret
- `MAILER_USER` - mailer system username
- `MAILER_PASSWORD` - mailer system password
- `HAT_USER` - username of the dedicated dataplug account on HATs
- `HAT_PASSWORD` - password of the dedicated dataplug account on HATs
- `MS_DATAPLUG_ID` - dataplug ID on MarketSquare registry
- `MS_ACCESS_TOKEN` - access token for MarketSquare
- `SERVICES_SECRET` - shared secret for HATDeX's HAT services
- `DATABASE_URL` - database URL
- `DATABASE_USER` - database username
- `DATABASE_PASSWORD` - database password
- `COOKIE_SIGNER_KEY` - cookie signer key
- `CRYPTER_KEY` - crypter key

#### Twitter-specific variables
- `TWITTER_CONSUMER_KEY` - Twitter app's consumer key
- `TWITTER_CONSUMER_SECRET` - Twitter app's consumer secret

You can then run a dataplug locally by executing

    ./deploy/run.sh
    
    OR
    
    sbt "project dataplug-{providerName}" "run"
