# TCK Tests

This module runs the OSGi RSA TCK tests, to make sure the RSA implementation is fully compliant with the specs.

## Run the tests

To run all tests:

    mvn verify

To run a specific test:

    mvn verify -Dtck.test=<fullly.qualified.TestClass[:method]>
