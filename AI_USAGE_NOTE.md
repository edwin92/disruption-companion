# AI Usage & Engineering Decision Note

**Candidato:** Edwin Ferreira  
**Desafío:** Software Engineer Backend Senior — Caso A (*Disruption Companion*)

---

## 1. Herramientas de IA Utilizadas y Propósito

Durante el desarrollo se empleó asistencia de IA bajo una metodología estricta **Spec-Driven Development (SDD) + Test-Driven Development (TDD)**:
1. **Generación acelerada de estructura Hexagonal y pruebas parametrizadas:** Construcción de *records* inmutables de dominio en Java 25, adaptadores JPA/REST y baterías de pruebas de borde (SQLi/XSS/SSTI/CRLF, violaciones de protocolo HTTP `400/404/405/406/409/415/422/503`).
2. **Automatización de contrato OpenAPI basado en evidencia real:** Ejecución de una matriz automatizada de **68 casos E2E reales** contra el servicio vivo para poblar [`openapi.yaml`](./openapi.yaml) con las respuestas reales producidas por el motor.
3. **Auditoría continua de seguridad y arquitectura:** Verificación automatizada con **Snyk Code (`snyk_code_scan`)** (`0` hallazgos) y **ArchUnit** (`98` pruebas automatizadas en total).

---

## 2. Qué se Aceptó, Qué se Rechazó y Qué se Modificó (y Cómo se Validó)

| Propuesta / Salida de IA | Decisión | Por qué y Cómo se Validó |
|---|---|---|
| **Delegar el cálculo de elegibilidad y selección de vuelos (`O1`/`O2`/`O3`) a un agente LLM con *tool-calling*** | **Rechazado** | En operaciones aéreas, permitir que un modelo probabilístico decida si una tarifa `LIGHT` paga penalidad o si un vuelo con `1` asiento sirve para `2` pasajeros introduce riesgo regulatorio y financiero inaceptable. Se implementó `DisruptionDomainEngine` 100% determinista y se validó con `DisruptionDomainEngineTest`. |
| **Idempotencia simple (*read-then-write* después de llamar al *upstream*) y reintento ante `503 UPSTREAM_TIMEOUT`** | **Modificado** | Hacer *lookup* antes del *upstream* y guardar solo al final deja una ventana de carrera ante peticiones concurrentes o caídas de proceso. Se modificó a **Claim-First (`IN_PROGRESS`)**: se inserta primero el registro en PostgreSQL (la restricción `UNIQUE(booking_id, idempotency_key)` frena hilos concurrentes antes de invocar `create_rebooking_request`) y luego se actualiza a `PENDING_CONFIRMATION` o a `INDETERMINATE_PENDING_RECONCILIATION` si ocurre un `503`. Validado en `DisruptionUseCasesTest` y `DisruptionIntegrationTest`. |
| **Exponer el borrador del modelo de IA directamente al pasajero en consultas abiertas y crear múltiples tickets humanos** | **Modificado** | Se diseñó `AiSafetySandwichService` con post-validación determinista (`policy_ref == POL-DELAY-2026-03`, `confidenceScore >= 0.85`, bloqueo de vuelos descartados `LA2450`/`O2` y bloqueo de promesas de efectivo `R4`) y se garantizó un único caso humano activo por reserva (`auto-inq-<bookingId>`) para no saturar la cola `DISRUPTION_L2`. |
| **Suite ArchUnit, Testcontainers PostgreSQL 17 y filtro de URI contra Matrix Parameters (`;DROP`)** | **Aceptado** | Se detectó en pruebas E2E que Spring MVC recorta parámetros de matriz (`;DROP`) en variables de ruta. Se integró `StrictHttpFirewallFilter` para rechazar `;`, `..` y CRLF con `400 Bad Request` JSON, complementando el bloqueo a nivel de conector Tomcat (`%2F`, `%00`). |

---

## 3. Decisiones de Diseño Gobernadas por el Candidato

1. **Arquitectura "Sándwich de Seguridad" (Determinístico $\rightarrow$ Puerto IA Calibrado $\rightarrow$ Guardarraíl Determinista):** Separar las decisiones operativas/financieras (motor determinista puro) de la atención de consultas abiertas (`AiInferencePort` calibrado con `temperature = 0.0`, umbral `0.85` y *triggers* de simulación gobernados por `disruption.ai.simulation-triggers-enabled`).
2. **Reconciliación y degradación de fuentes (`get_operational_events`):** Descartar toda fuente listada en `_meta.sources_degraded` (`LEGACY_MIRROR`) y exigir escalamiento humano (`requiresHumanApproval = true`) ante contradicción entre fuentes saludables (`CONFL1`) o caída total (`ALLDEG` $\rightarrow$ `503`).
3. **Resolución del campo faltante `fare_conditions.change_penalty_amount`:** Determinar que la pérdida de conexión verificada (`missed_connection = true`) activa `R3` (`REBOOK_NO_FEE`), evitando bloquear al pasajero por el monto de penalidad ausente en su tarifa `LIGHT`.
4. **Persistencia real sin transacciones largas sobre red:** `spring.jpa.open-in-view=false` y transacciones cortas por operación (`saveAndFlush`), evitando retener conexiones del pool JDBC mientras se espera a sistemas *upstream*.

---

## 4. Frontera de Privacidad: Qué Datos NUNCA se Envían a un LLM

Por diseño (*Privacy by Design* en `AiSafetySandwichService.buildSanitizedPromptContext`), **ningún dato personal identificable (PII)** sale hacia `AiInferencePort`:
- **Nunca se envía:** Nombre completo (`María Silva`, `Tomás Silva`), documento/RUT (`12.345.678-9`), correo (`maria.silva@example.com`) ni teléfono (`+56 9 8765 4321`).
- **Redacción activa en la pregunta del usuario:** Si el pasajero escribe su RUT, correo, teléfono o nombre en `question`, `redactPii(...)` los reemplaza por `[REDACTED_DOCUMENT]`, `[REDACTED_EMAIL]`, `[REDACTED_PHONE]` y `[REDACTED_NAME]` antes de invocar el puerto de IA (validado en `AiSafetySandwichValidatorTest`).
- **Sanitización de Logs (CWE-117):** Todo log pasa por `LogSanitizer.sanitize(...)` con `OWASP Java Encoder`, sin registrar entradas rechazadas ni PII.
