# Intelligent Translation Agent API

**Owner:** Anuj Sridharan

## Description

A middleware API that accepts a mapping file (typically authored by a Business Analyst), a Source JSON Schema, and a Target JSON Schema. It calls the Anthropic Claude API to generate translation logic between the two schemas, caches the resulting algorithm, and reuses it for all subsequent calls to transform source payloads into target payloads.

> **Key highlight:** Source and target *data* is never sent to Claude — only the schemas and mapping file. The algorithm is generated once, downloaded, and executed locally for all translations.


## Exposed APIs

| Method | Endpoint                         | Description                                                                                          |
|--------|----------------------------------|------------------------------------------------------------------------------------------------------|
| `POST` | `/rest/admin/download-algorithm` | Generates and stores a translation algorithm locally into the algorithm cache — one-time admin setup |
| `POST` | `/rest/transform`                | Transforms a source JSON payload into the target JSON format                                         |
| `GET`  | `/rest/admin/algorithms`         | Lists all algorithms currently loaded in the cache                                                   |


## In-Flight Development

- REST to SOAP
- SOAP to REST