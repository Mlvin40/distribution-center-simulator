# REDXela

Simulador de centro de distribución: backend Java y SPA Vue independientes.

## Ejecutar en desarrollo

Requisitos: Java 21, Node 24.22.1 (o una versión compatible con `redxela-client/package.json`).

```bash
cd redxela-api
./mvnw spring-boot:run
```

En otra terminal:

```bash
cd redxela-client
npm ci
npm run dev
```

Abrir http://localhost:5173. El proxy `/api` apunta a http://localhost:8080. `/api/health` devuelve el estado de conectividad. El proxy es de desarrollo; una publicación futura necesitará enrutar `/api` al backend.

## Validar

```bash
cd redxela-api
./mvnw test
./mvnw -DskipTests clean package
```

```bash
cd redxela-client
npm run build
npm run lint
```
