# Extra CA certificates

Put any additional root CA certificates (PEM format, `*.crt`) in this folder.
They are imported into the Docker images at build time:

- backend build stage: Java truststore used by Gradle
- backend runtime: Java truststore used for Alipay gateway calls
- frontend build stage: Node.js (`NODE_EXTRA_CA_CERTS`) used by npm

This is only needed on networks with TLS inspection (for example a corporate proxy).
On a normal network, leave the folder empty.
