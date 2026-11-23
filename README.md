# DocReader.ai — Enterprise Document Intelligence & RAG Platform

<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.4-brightgreen?logo=springboot" alt="Spring Boot 3.2.4">
  <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk" alt="Java 17">
  <img src="https://img.shields.io/badge/Angular-17-red?logo=angular" alt="Angular 17">
  <img src="https://img.shields.io/badge/OpenAPI-3.0-blue?logo=openapiinitiative" alt="OpenAPI 3.0">
  <img src="https://img.shields.io/badge/Swagger-UI-green?logo=swagger" alt="Swagger UI">
  <img src="https://img.shields.io/badge/Security-Hardened-blueviolet?logo=auth0" alt="Security Hardened">
  <img src="https://img.shields.io/badge/License-Apache_2.0-lightgrey" alt="License">
</p>

**DocReader.ai** is an executive-grade Retrieval-Augmented Generation (RAG) document intelligence platform. It ingests complex business contracts, agreements, and multi-page technical documents (PDF, DOCX, TXT/MD), extracts text with exact page-level fidelity, performs semantic sliding-window chunking with vector embeddings, and delivers real-time grounded AI answers with bracketed verbatim source citations `[1]`, `[2]`.

---

## ⚡ Quick Start (One Command)

Start both Spring Boot backend and Angular frontend concurrently with health verification:

```bash
# Start backend (port 8080) and frontend (port 4200)
./start.sh --wait

# Stop all running background services
./stop.sh
```

- **Web Application UI**: [http://localhost:4200/#demo](http://localhost:4200/#demo)
- **Interactive Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI 3 Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Backend Health Check**: [http://localhost:8080/api/health](http://localhost:8080/api/health)

---

## 🏗️ Architecture Overview

```
                      +------------------------------------------+
                      |          Angular Frontend (SPA)          |
                      |  - PDF / DOCX / TXT Document Uploader   |
                      |  - Word-by-Word SSE Token Streaming     |
                      |  - Verbatim Bracketed Citations [1]      |
                      |  - Frictionless Guest Demo Mode         |
                      +--------------------+---------------------+
                                           | HTTP / SSE
                                           v
                      +------------------------------------------+
                      |         Spring Boot 3.2.4 Backend        |
                      |  +------------------------------------+  |
                      |  | Security & Hardened Defense Layer  |  |
                      |  | - JWT (HS256 with SHA-256 derive)  |  |
                      |  | - IP-Based Sliding Window Limiter  |  |
                      |  | - Anti-Clickjacking (SameOrigin)   |  |
                      |  | - Restricted CORS Policy           |  |
                      |  +------------------------------------+  |
                      |  +------------------------------------+  |
                      |  | RAG Engine & Ingestion Pipeline    |  |
                      |  | - PDFBox & POI (Zip Bomb Guarded)  |  |
                      |  | - Semantic Sliding Window Chunker  |  |
                      |  | - Vector Similarity (Dense 1536d)  |  |
                      |  | - XML-Quarantined Meta Prompting   |  |
                      |  | - SseEmitter Real-Time Streaming   |  |
                      |  +------------------------------------+  |
                      |  +------------------------------------+  |
                      |  | OpenAPI 3 / Swagger Documentation  |  |
                      |  +------------------------------------+  |
                      +-------+--------------------------+-------+
                              |                          |
                              v                          v
             +----------------------------------+    +----------------------------+
             |   PostgreSQL / In-Memory H2      |    | Remote Inference Provider  |
             | - Users & Guest Demo Accounts    |    | - Groq (Qwen 3.8 27B)      |
             | - Document Metadata & Chunks     |    | - OpenAI (GPT-4o Mini)     |
             | - 1536d Embeddings & Q&A History |    | - Local Extractive Fallback|
             +----------------------------------+    +----------------------------+
```

---

## 🛡️ Enterprise Security Hardening

DocReader.ai implements comprehensive defenses against OWASP Top 10 for LLMs and common web attack vectors:

| Defense | Severity | Technical Implementation |
| :--- | :--- | :--- |
| **Indirect Prompt Injection (OWASP LLM01)** | **High** | Document chunks are quarantined inside `<document_context><source id="..." page="...">` XML fences. System meta-prompts strictly order the model to treat document content as untrusted passive reference data and ignore embedded instructions (e.g. *"ignore previous instructions"*). |
| **Denial of Wallet & Session Flooding** | **High** | Dual-mode rate limiting: Distributed Redis with native TTL (`demo:ip:{clientIp}`, `demo:usage:{userId}`) in production, automatically falling back to in-memory sliding window when Redis is disabled/offline. Excessive guest session generation triggers **HTTP 429 Too Many Requests**. |
| **Zip Bomb & File DoS Protection** | **Medium** | POI `ZipSecureFile.setMinInflateRatio(0.005)` prevents expansion bombs in `.docx`. Extraction is strictly capped at `150 pages` and `1,000,000 characters` (~150k words). |
| **Malicious Upload & Path Traversal** | **Medium** | Sanitized paths using `Paths.get().getFileName()`. Strict file extension whitelisting (`.pdf`, `.docx`, `.txt`, `.md`). Non-whitelisted files immediately return **HTTP 400 Bad Request**. |
| **Clickjacking (UI Redressing)** | **Medium** | Enforced `X-Frame-Options: SAMEORIGIN` across all responses, preventing malicious sites from embedding the application inside `<iframe>`s. |
| **Strict CORS Origin Whitelisting** | **Medium** | Replaced wildcard CORS with explicit allowed origin lists (localhost and configurable production domains). Unauthorized cross-origin requests receive **HTTP 403 Forbidden**. |
| **Cryptographic JWT Key Derivation** | **Low** | Automatically derives a 256-bit key via cryptographic SHA-256 if the provided secret is short, preventing HMAC-SHA weak-key vulnerabilities. |
| **Database Console Isolation** | **Low** | In-memory H2 Console has `web-allow-others: false`, restricting access strictly to localhost. |

---

## ⚡ Distributed Redis with TTL (Session & Quota Management)

DocReader.ai provides an enterprise-ready dual-mode session and rate-limiting subsystem:

- **Native Redis TTL Keys**:
  - `demo:session:{userId}`: Guest session metadata with 60-minute expiration window.
  - `demo:usage:{userId}`: Atomic query counter (`INCR`) with 60-minute window TTL.
  - `demo:ip:{clientIp}`: Atomic IP registration counter with 1-hour sliding TTL.
- **Graceful Zero-Crash Fallback**:
  - **Local Dev / Free Tier**: Runs 100% in-memory out-of-the-box (`DOCUREADER_REDIS_ENABLED=false`) with zero external service dependencies.
  - **Production Ready**: Enable with `DOCUREADER_REDIS_ENABLED=true` and `REDIS_URL`. If Redis is temporarily unreachable, all rate limiting automatically and seamlessly degrades to in-memory tracking without throwing 500 errors to users.

---

## 📖 Swagger / OpenAPI 3 Documentation

The backend includes a fully interactive Swagger UI and OpenAPI 3 schema:

- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **API Spec (JSON)**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Interactive Authentication in Swagger
1. Call `POST /api/demo/start` or `POST /api/auth/login` to obtain a JWT.
2. Click the green **"Authorize"** button at the top-right of the Swagger UI.
3. Paste the token into the `BearerAuth` field and click **Authorize**.
4. Test protected endpoints (`/api/documents`, `/api/chat/stream`, etc.) directly from your browser!

---

## 📋 REST API Reference

### 🔐 Authentication & Guest Demo
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Register new user account and obtain JWT |
| `POST` | `/api/auth/login` | Authenticate user credentials and obtain JWT |
| `POST` | `/api/demo/start` | Instant guest session token (5 free queries, IP rate limited) |
| `GET` | `/api/demo/status` | Check guest session status and remaining queries |

### 📄 Document Ingestion
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/documents/upload` | Upload & ingest PDF, DOCX, TXT/MD (multipart/form-data) |
| `GET` | `/api/documents` | List all indexed documents for authenticated user |
| `GET` | `/api/documents/{id}` | Get metadata and page count for specific document |
| `DELETE`| `/api/documents/{id}` | Delete document and purge vector chunk embeddings |

### 💬 Chat & Grounded RAG
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/chat/stream` | Stream AI response via Server-Sent Events (`text/event-stream`) |
| `GET` | `/api/chat/sessions` | List past chat sessions |
| `GET` | `/api/chat/sessions/{id}` | Get full conversation history with citations |

### 🩺 System Health
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/health` | Service health status and platform metadata |

---

## ⚙️ Configuration & Environment Variables

Key parameters in `backend/src/main/resources/application.yml`:

| Environment Variable | Default Value | Description |
| :--- | :--- | :--- |
| `OPENAI_API_KEY` | `(pre-configured Groq key)` | API key for remote LLM inference (Groq or OpenAI) |
| `OPENAI_MODEL` | `qwen/qwen3.8-27b` | LLM model identifier |
| `OPENAI_BASE_URL` | `https://api.groq.com/openai/v1` | Base URL for OpenAI-compatible chat API |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | Vector embedding model name |
| `DOCUREADER_JWT_SECRET` | `(256-bit default hex)` | HMAC secret key for signing JWT tokens |
| `DOCUREADER_CORS_ORIGINS` | `http://localhost:4200,...` | Comma-separated list of allowed CORS origins |

---

## 💻 Manual Setup & Local Development

### Prerequisites
- **Java 17+** (OpenJDK 17 recommended)
- **Maven 3.6+**
- **Node.js 18+** & **npm**

### 1. Backend Setup
```bash
cd backend
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
mvn clean package -DskipTests
java -jar target/docureader-ai-backend-1.0.0.jar
```
Backend will be live at `http://localhost:8080`.

### 2. Frontend Setup
```bash
cd frontend
npm install --legacy-peer-deps
npm start
```
Frontend will be live at `http://localhost:4200`.

### 3. Docker Deployment
```bash
docker compose -f deploy/docker-compose.yml up --build
```
Launches:
- Frontend Nginx on `http://localhost:80`
- Spring Boot Backend on `http://localhost:8080`
- PostgreSQL with `pgvector` on port `5432`

---

## 📄 License
This project is licensed under the [Apache License 2.0](LICENSE).
