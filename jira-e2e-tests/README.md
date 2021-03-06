# Jira plugin end-to-end testing framework

This is an end-to-end/functional testing framework for the plugin; currently it
targets Jira, but should be adaptable to other products.

NOTE: This is a work-in-progress, however the Cypress smoke-test suite is
currently run as part of CI; see the section below.

## Testing framework structure

The testing framework consists of the following parts:

* A wrapper for official Postgres Docker image which injects a pre-configured Jira database.
* A wrapper for the official Atlassian Jira-Software image
* A [Cypress](https://www.cypress.io/) test-suite and Docker image
* A functional (REST API) test-suite and Docker image
* A Docker-Compose config to configure and run the above images and a smoke-test suite.

### Postgres container setup

The wrapper Docker image injects a pre-configured Jira database. The database
has had the license field removed, and in must be injected before use. The
license can be aquired from several places:

* Members of the DC Deployments team have access to "Trebuchet Jira E2E smoketest license 2"
  in [LastPass](https://lastpass.com).
* Other Atlassians can generate a license via the [License Encoder
  Service](https://license-encoder-service--app.ap-southeast-2.dev.atl-paas.net/).
* Non-Atlassians can generate an evaluation license at
  [my.atlassian.com](https://my.atlassian.com/).

If generating a new license the Server ID is `BEEW-DT3K-EFO9-XNDI`.

Once acquired the license can be injected with:

    cd jira-e2e-tests
    export JIRA_E2E_LICENSE='xxxxx'
    ./postgres/inject-license

Data is pre-loaded from a pg_dump of a jira software 7.13.0 that is saved under jira.sql.tmpl. If you need to re-generate
this you will need to:
 - Start jira software version of choice and setup to point to a postgres database (lets call it newDB)
 - Go through setup, create sample project (any will do), take note of the server id, shutdown the instance
 - `pg_dump newDB > jira.sql.tmpl`. Ensure to copy jira.sql.tmpl to correct directory, replacing existing file.
 - In `jira.sql.tmpl`:
 
    - find the line after `COPY productlicense (id, license) FROM stdin;` and replace with 
    `10000	INJECT_LICENSE_HERE`
    - find any reference to your server id, replace with BFAP-W4RG-46TJ-B5TK
 -  run `./postgres/inject-license`
   

### Jira container setup

The Jira container configuration injects a copy of the plugin, which should have
been placed in the container directory. For local runs this can be done with:

    mvn clean package -DskipTests
    rm -f jira-e2e-tests/jira/jira-plugin-*.jar && cp jira-plugin/target/jira-plugin-*.jar jira-e2e-tests/jira/

### Cypress container setup

The Cypress container mainly copies the tests into the container. However some
tests exercise the AWS functionality via the plugin, and require a valid AWS
key/secret pair. These should be set via the environment via:

    export CYPRESS_AWS_ACCESS_KEY_ID='XXXX'
    export CYPRESS_AWS_SECRET_ACCESS_KEY='YYYY'
    export CYPRESS_ADMIN_PASSWORD="ZZZZ"

Members of the DC Deployments team should have access to these in LastPass.

## Running the test suite

### Running locally

This mostly involves setting the environment correctly as above and running
`docker-compose`. There is a helper script in `jira-e2e-tests/helpers/run-local`
to assist with this. By default, it will run Jira and Postgres, which are
accessible via [http://localhost:2990/jira/]:

    export JIRA_E2E_LICENSE='xxxxx'
    export CYPRESS_AWS_ACCESS_KEY_ID='XXXX'
    export CYPRESS_AWS_SECRET_ACCESS_KEY='YYYY'
    export CYPRESS_CONTEXT=amps
    ./jira-e2e-tests/helpers/run-local

To run the functional or UI tests you can pass the container name to the script
after setting any additional variables, e.g:

    export JIRA_E2E_LICENSE='xxxxx'
    export CYPRESS_AWS_ACCESS_KEY_ID='XXXX'
    export CYPRESS_AWS_SECRET_ACCESS_KEY='YYYY'
    ./jira-e2e-tests/helpers/run-local cypress

or:

    export JIRA_E2E_LICENSE='xxxxx'
    ./jira-e2e-tests/helpers/run-local functests

Once the test suite is complete the test container will shut-down but the
Jira instance and DB will remain up. This can be used to run the smoke-test or
other Cypress tests via the UI:

    cd cypress
    yarn cypress

While this is running you can update the installed plugin using the helper
script `helpers/upload-plugin`.

Note that the run-local (and GitHub Actions tests) run with transition-checking
enforced. To allow any transition for local development add
`--allowAnyTransition` to `run-local`.


## Continuous integration

The basic smoke-test runs automatically in GitHub Actions on each push. See the
file [Actions config](../.github/workflows/mvn_test.yml) for details. The secret
environment variables are set in the Github secrets under Settings.
