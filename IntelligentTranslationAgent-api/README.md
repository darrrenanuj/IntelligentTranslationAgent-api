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

Mapping File
<img width="1238" height="624" alt="image" src="https://github.com/user-attachments/assets/5e8733df-8afa-4d31-aa19-3b053e4a1e72" />

Download Algorithm
<img width="2702" height="1538" alt="image" src="https://github.com/user-attachments/assets/1f607098-05cf-4c96-8cfb-49d763c97ef6" />

Translate
<img width="2702" height="1538" alt="image" src="https://github.com/user-attachments/assets/84f0fa5c-b836-4f83-a61c-075a3da1191e" />

List Algorithms
<img width="2702" height="1538" alt="image" src="https://github.com/user-attachments/assets/16c97039-2b06-4e84-9ea7-95997d8fcdd3" />


## In-Flight Development

- REST to SOAP
- SOAP to REST
