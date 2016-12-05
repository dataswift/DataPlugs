#!/usr/bin/env bash

jdbcurl=$JDBCURL
dbuser=$DBUSER
dbpass=$DBPASS

runEvolutions() {

local key CONTEXTS TASK

# Use > 1 to consume two arguments per pass in the loop (e.g. each
# argument has a corresponding value to go with it).
# Use > 0 to consume one or more arguments per pass in the loop (e.g.
# some arguments don't have a corresponding value to go with it such
# as in the --default example).
# note: if this is set to > 0 the /etc/hosts part is not recognized ( may be a bug )
while [[ $# > 1 ]]
do
key="$1"

case $key in
    -c|--contexts)
    CONTEXTS="$2"
    shift # past argument
    ;;
    -t|--task)
    TASK="$2"
    shift # past argument
    ;;
    *)
            # unknown option
    ;;
esac
shift # past argument or value
done
(>&2 echo "Running Evolutions on $CONTEXTS contexts, task $TASK")

CLASSPATH="${SOURCE}/postgresql-9.4.1208.jre6.jar"

(>&2 echo "Classpath ${CLASSPATH}")

if [[ $TASK == 'dropAll' ]]; then
   echo "Drop All"
       liquibase --changeLogFile=evolution.sql \
          --username=$dbuser \
          --password=$dbpass \
          --url=$jdbcurl \
          --classpath=$CLASSPATH \
          --liquibaseSchemaName=public \
          --defaultSchemaName=public \
          dropAll
else
  if [[ $TASK == 'rollbackOne' ]]; then
      echo "Rollback One"
           liquibase --changeLogFile=evolution.sql \
              --username=$dbuser \
              --password=$dbpass \
              --url=$jdbcurl \
              --classpath=$CLASSPATH \
              --liquibaseSchemaName=public \
              --defaultSchemaName=public \
              rollbackCount 1
  else
    if [[ $TASK == 'update' ]]; then
      if [[ $CONTEXTS == '' ]]; then
        (>&2 echo "Must specify evolution contexts via -c or --contexts: 'structures', 'data', 'testdata' or a combination of those")
      else
        ## now loop through the above array
        for i in "${evolutions[@]}"
        do
           echo "Evolution $i.sql"
           liquibase --changeLogFile=$i.sql \
          --contexts=$CONTEXTS \
          --username=$dbuser \
          --password=$dbpass \
          --url=$jdbcurl \
          --classpath=$CLASSPATH \
          --liquibaseSchemaName=public \
          --defaultSchemaName=public \
          update
        done
      fi
    else
      if [[ $TASK == 'updateSQL' ]]; then
        for i in "${evolutions[@]}"
        do
           (>&2 echo "Evolution $i.sql")
           liquibase --changeLogFile=$i.sql \
          --contexts=$CONTEXTS \
          --username=$dbuser \
          --password=$dbpass \
          --url=$jdbcurl \
          --classpath=$CLASSPATH \
          --liquibaseSchemaName=public \
          --defaultSchemaName=public \
          updateSQL
        done
      fi
    fi
  fi
fi
}
