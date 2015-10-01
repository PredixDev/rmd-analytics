set -x
set -e
cf login -a https://api.grc-apps.svc.ice.ge.com --skip-ssl-validation -u ${CF_USERNAME} -p ${CF_PASSWORD} -o $ORG -s $SPACE
cf push $SERVICE -f manifest-integration.yml

# mvn clean test -s ../mvn_settings.xml  -gs ../mvn_settings.xml
# Clean up
