#!/bin/bash

set -e

BUCKET="elasticbeanstalk-eu-west-1-974966015544"
VERSION="fitbit"`git log --format="%H" -n 1`
APPLICATION_NAME="fitbit"
ENVIRONMENT_NAME="fitbit-stage-env"

echo "Build"
sbt "project CHANGEME" "docker:stage"

echo "Create package"
cp -r dataplug/deploy/Dockerrun.aws.json dataplug/deploy/.ebextensions dataplug/target/docker/stage
cd dataplug/target/docker/stage
zip -q -r $VERSION.zip * .ebextensions

echo "Upload package"
aws s3api put-object --bucket $BUCKET --key $VERSION --body $VERSION.zip

#echo "Deploy new version"
aws elasticbeanstalk create-application-version --application-name "$APPLICATION_NAME" --version-label "$VERSION" --source-bundle S3Bucket=$BUCKET,S3Key=$VERSION
aws elasticbeanstalk update-environment --environment-name "$ENVIRONMENT_NAME" --version-label "$VERSION"

echo "Cleanup"
rm $VERSION.zip
