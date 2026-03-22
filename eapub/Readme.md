# Remote Service Admin EventAdmin Publisher

This component publishes Remote Service Admin events to the
EventAdmin service, if present (see OSGi Compendium 8 section 122.7.1).

Since in some OSGi environments optional imports can cause trouble
(e.g. refreshes in Karaf), and EventAdmin is optional, this
functionality was extracted into a separate module so it can be
installed explicitly only when needed.
