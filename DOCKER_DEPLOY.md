# Docker deployment (Railway)

The repository root contains a multi-stage `Dockerfile` that builds the Vue frontend, packages it into the Spring Boot jar, and runs it with Java 17.

## Railway setup

1. Create a Railway project and deploy this GitHub repository, selecting the `render-demo` branch (use `main` for a stable release; use your working branch for demos).
2. In the service settings, set **Builder** to **Dockerfile** (use the repository-root `Dockerfile`). Leave the root directory empty.
3. Clear any custom Build Command, Pre-deploy Command, and Custom Start Command so Railway uses the Dockerfile's build steps and `CMD`.
4. Deploy the service. Railway injects `PORT`; the container command binds Spring Boot to that port.
5. In **Settings → Networking**, generate a public domain and open it to verify the demo.

No database is required. The demo stores scripts in a local JSON file and execution history in memory. Treat this as a disposable demo: data may be lost when the service is replaced or restarted unless you configure persistent storage.
