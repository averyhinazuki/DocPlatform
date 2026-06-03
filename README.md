\# DocPlatform



A multi-tenant document generation and notification platform.



!\[CI](https://github.com/averyhinazuki/DocPlatform/actions/workflows/ci.yml/badge.svg)



\## Stack



\- \*\*Backend:\*\* Java 21, Spring Boot 3, MyBatis-Plus, MongoDB, Kafka, Redisson, MinIO

\- \*\*Frontend:\*\* Vue 3, Vite, Pinia



\## Features



\- Multi-tenant report generation (PDF, Excel, CSV)

\- Kafka-driven async job pipeline with exactly-once delivery

\- Tenant concurrency quota (Redis atomic counter, HTTP 429)

\- Real-time notifications via SSE + Redis pub/sub

\- Report assignments and scheduling

\- Rich text editor for static PDF templates

