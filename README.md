# Metabase Backend

Spring Boot backend for a connected media catalogue.

## Development

```sh
docker compose up -d
./mvnw test
```

GraphiQL is available at `http://localhost:8080/graphiql` while the application is running.

Java formatting uses Prettier with the Java plugin:

```sh
npm install
npm run format
npm run format:check
```
