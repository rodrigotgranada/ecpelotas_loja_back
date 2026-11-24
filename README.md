# Loja Oficial Alcateia — Backend API (E.C. Pelotas)

API REST em **Java 17 + Spring Boot 3.4 + PostgreSQL 17 + Flyway** para a vitrine pública, pré-reservas WhatsApp, estoque Kardex por XML de NF-e e PDV assistido da Loja Oficial Alcateia.

## Portas Locais (paralelas ao Portal)
- **API:** `http://localhost:7996` (Portal usa `7998`)
- **Health Check:** `http://localhost:7996/health`
- **Swagger UI:** `http://localhost:7996/swagger-ui.html`
- **PostgreSQL Docker:** `localhost:5434` (`ecpelotas_loja_local` — Portal usa `5433`)

## Subir Localmente
```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

## Testes
```powershell
.\mvnw.cmd test
```
