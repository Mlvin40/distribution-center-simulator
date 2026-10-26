# REDXela

Simulador de centro de distribución: backend Java y SPA Vue independientes.

## Ejecutar en desarrollo

Requisitos: Java 21, Node 24.22.1 (o una versión compatible con `redxela-client/package.json`).

```bash
cd redxela-api
./mvnw spring-boot:run
```

En otra terminal:

```bashmelvin@mlvin:~/Escritorio/SISTEMAS_OPERATIVOS_2/PROYECTO_FINAL$ git status
En la rama main
Tu rama está actualizada con 'origin/main'.

Cambios no rastreados para el commit:
  (usa "git add <archivo>..." para actualizar lo que será confirmado)
  (usa "git restore <archivo>..." para descartar los cambios en el directorio de trabajo)
        modificados:     README.md

Archivos sin seguimiento:
  (usa "git add <archivo>..." para incluirlo a lo que será confirmado)
        .gitignore
        redxela-api/
        redxela-client/

sin cambios agregados al commit (usa "git add" y/o "git commit -a")
melvin@mlvin:~/Escritorio/SISTEMAS_OPERATIVOS_2/PROYECTO_FINAL$ 
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
