# API de Gestión de Pólizas

API REST para administrar pólizas de arrendamiento y los inmuebles (riesgos) que
cubren: consulta con filtros, renovación con aplicación del IPC, cancelación de
pólizas y alta y cancelación de riesgos. Cada cambio de estado se notifica a un
sistema externo (el CORE), representado aquí por un mock.

## Stack

| Componente | Versión |
|---|---|
| Spring Boot | 4.1.2 |
| Java | 21 |
| Maven | wrapper (`./mvnw`), 3.10.0 |
| PostgreSQL | 16 (alpine) |
| Empaquetado | jar ejecutable, imagen Docker multi-stage |

Spring Web MVC, Spring Security, Spring Data JPA, Bean Validation y Actuator.
Pruebas con JUnit 5, Mockito, AssertJ, MockMvc y Testcontainers.

---

## Requisitos

**Solo Docker.** El build, las pruebas y la aplicación corren en contenedores con
JDK 21, así que no hace falta tener Java ni Maven instalados.

Java local es **opcional**. Si quieres ejecutar `./mvnw` directamente en tu
máquina, necesitas **JDK 21** y `JAVA_HOME` apuntando a él:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw test   # macOS
```

---

## Arranque

```bash
docker compose up --build
```

Levanta dos servicios: `db` (PostgreSQL 16) y `app`. El servicio `app` espera a
que la base esté *healthy* antes de arrancar, y al primer arranque carga datos de
ejemplo. No hay pasos manuales.

| Qué | Dónde |
|---|---|
| API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| PostgreSQL (desarrollo) | `localhost:5434` — usuario, clave y base: `polizas` |

El Postgres de desarrollo se expone en el **5434** y no en el 5432 para no chocar
con instalaciones locales de PostgreSQL, que son frecuentes y dejan el puerto
estándar ocupado. Dentro de la red de Compose la base sigue siendo `db:5432`.

Para inspeccionar la base:

```bash
docker compose exec db psql -U polizas -d polizas -c "select id, numero, tipo, estado from poliza order by id;"
```

Para volver al estado inicial (borra el volumen y recarga los datos de ejemplo):

```bash
docker compose down -v && docker compose up --build -d
```

### Datos de ejemplo

Los carga un `DataSeeder` al arrancar, **solo si la base está vacía**, de modo que
reiniciar la aplicación no los duplica.

| Póliza | Número | Tipo | Estado | Riesgos |
|---|---|---|---|---|
| 1 | POL-IND-001 | INDIVIDUAL | VIGENTE | 1 (ACTIVO) |
| 2 | POL-COL-001 | COLECTIVA | VIGENTE | 3 (ACTIVO) |
| 3 | POL-COL-002 | COLECTIVA | CANCELADA | 1 (CANCELADO) |

---

## Pruebas

```bash
./scripts/test.sh
```

Corre las 115 pruebas dentro de un contenedor con JDK 21. **Requiere Docker
encendido**: los tests de integración levantan un PostgreSQL real con
Testcontainers.

El script monta el socket de Docker del host, así que los contenedores que crean
los tests quedan como *hermanos* del contenedor de Maven y no como hijos. Por eso
pasa `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`: es la dirección con la
que el contenedor de Maven alcanza los puertos que esos hermanos publican en el
host. El repositorio Maven se cachea en el volumen `demo-ti-m2`, así que la
primera ejecución descarga dependencias y las siguientes no.

Acepta argumentos de Maven:

```bash
./scripts/test.sh -Dtest=PolizaTest
```

---

## Endpoints

Todos exigen el header `x-api-key: 123456`. La única excepción es
`/actuator/health`.

| # | Método | Ruta | Éxito |
|---|---|---|---|
| 1 | GET | `/polizas?tipo=&estado=` | 200 |
| 2 | GET | `/polizas/{id}/riesgos` | 200 |
| 3 | POST | `/polizas/{id}/renovar` | 200 |
| 4 | POST | `/polizas/{id}/cancelar` | 200 |
| 5 | POST | `/polizas/{id}/riesgos` | 201 |
| 6 | POST | `/riesgos/{id}/cancelar` | 200 |
| 7 | POST | `/core-mock/evento` | 202 |

La colección completa, con un caso por endpoint y por cada error relevante, está
en [`requests.http`](requests.http) y se puede ejecutar desde IntelliJ IDEA o con
la extensión REST Client de VS Code.

## Colección de Postman

Para probar los endpoints uno a uno desde Postman, el repositorio incluye una colección lista para importar en `postman_collection/DemoTI_Polizas.postman_collection.json`.

### Importar

1. Abre Postman y elige **Import**.
2. Selecciona el archivo `postman_collection/DemoTI_Polizas.postman_collection.json`.
3. Con la API levantada (`docker compose up --build`), ejecuta las peticiones en orden, de arriba hacia abajo.

### Verificar notificaciones y CORE

Postman solo muestra la respuesta HTTP, así que las notificaciones simuladas se comprueban en los logs de la aplicación. Cada petición POST indica en su descripción qué debe aparecer:

```bash
docker compose logs app | grep -E "NOTIFICACION|CORE-MOCK"
```

| Operación | `[NOTIFICACION]` | `[CORE-MOCK]` |
|---|---|---|
| Renovar póliza | Sí | Sí |
| Agregar riesgo | Sí | Sí |
| Cancelar póliza o riesgo | No | Sí |
| Cualquier operación fallida (400, 404, 409) | No | No |

### Contenido

La colección tiene 31 peticiones numeradas y agrupadas en carpetas, en el orden en que conviene ejecutarlas:

| Carpeta | Qué prueba |
|---|---|
| Auth (x-api-key) | Sin clave y con clave incorrecta devuelven 401; con la clave correcta pasa. Incluye el health check público. |
| Consultas | Listado de pólizas con filtros `tipo` y `estado`, y riesgos de una póliza. |
| Renovar póliza | IPC por defecto, IPC en el body, IPC fuera de rango, póliza cancelada e inexistente. |
| Riesgos | Alta de riesgo (colectiva, individual, cancelada) y cancelación de riesgos. |
| CORE mock | Evento válido e inválido hacia `/core-mock/evento`. |
| Cancelar póliza (al final) | Cancelación de la póliza y de todos sus riesgos, y casos de error. |

Todas las peticiones, salvo las de la carpeta Auth que prueban la ausencia o el error de la clave, envían el header `x-api-key: {{apiKey}}`.

### Variables

Se definen a nivel de colección y se pueden editar en la pestaña **Variables**:

| Variable | Valor por defecto | Uso |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Dirección de la API |
| `apiKey` | `123456` | Valor del header `x-api-key` |
| `polizaIndividualId` | `1` | Póliza individual de los datos semilla |
| `polizaColectivaId` | `2` | Póliza colectiva de los datos semilla |
| `polizaCanceladaId` | `3` | Póliza colectiva ya cancelada de los datos semilla |
| `riesgoNuevoId` | (vacía) | Se llena sola al agregar un riesgo y se usa al cancelarlo |

### Tests automáticos

Cada petición incluye un test que verifica el código HTTP esperado, y algunas comprueban además el cuerpo de la respuesta (por ejemplo, el estado `RENOVADA` o `CANCELADA`). Se pueden ejecutar todas juntas con el **Collection Runner** de Postman.

### Repetir la prueba

La carpeta final cancela la póliza colectiva y sus riesgos, por lo que una segunda corrida sobre los mismos datos devolvería resultados distintos. Para volver al estado inicial:

```bash
docker compose down -v
docker compose up --build -d
```

### 1. Listar pólizas

Ambos filtros son opcionales y combinables.

```bash
curl -s -H "x-api-key: 123456" "http://localhost:8080/polizas"
curl -s -H "x-api-key: 123456" "http://localhost:8080/polizas?tipo=COLECTIVA"
curl -s -H "x-api-key: 123456" "http://localhost:8080/polizas?estado=VIGENTE"
curl -s -H "x-api-key: 123456" "http://localhost:8080/polizas?tipo=COLECTIVA&estado=CANCELADA"
```

```json
[
  {
    "id": 1,
    "numero": "POL-IND-001",
    "tipo": "INDIVIDUAL",
    "estado": "VIGENTE",
    "tomador": "Ana Gomez",
    "fechaInicioVigencia": "2026-01-01",
    "fechaFinVigencia": "2027-01-01",
    "mesesVigencia": 12,
    "canonMensual": 1800000.00,
    "prima": 21600000.00
  }
]
```

`tipo` acepta `INDIVIDUAL` y `COLECTIVA`; `estado` acepta `VIGENTE`, `RENOVADA` y
`CANCELADA`. Un valor distinto devuelve 400 y el mensaje lista los válidos.

### 2. Riesgos de una póliza

```bash
curl -s -H "x-api-key: 123456" http://localhost:8080/polizas/2/riesgos
```

```json
[
  {
    "id": 2,
    "polizaId": 2,
    "direccionInmueble": "Carrera 7 # 45-10, Bogota",
    "arrendatario": "Luisa Martinez",
    "arrendador": "Inmobiliaria Andina SAS",
    "estado": "ACTIVO"
  }
]
```

### 3. Renovar una póliza

El body es **opcional**. Sin body, o con un body sin `ipc`, se aplica el IPC por
defecto (`polizas.ipc = 5.2`).

```bash
# con el IPC por defecto
curl -s -X POST -H "x-api-key: 123456" http://localhost:8080/polizas/1/renovar

# sobrescribiendo el IPC
curl -s -X POST -H "x-api-key: 123456" -H "Content-Type: application/json" \
  -d '{"ipc": 6.1}' http://localhost:8080/polizas/1/renovar
```

Devuelve la póliza actualizada: canon y prima recalculados, vigencia corrida un
periodo y estado `RENOVADA`.

### 4. Cancelar una póliza

Cancela la póliza y arrastra todos sus riesgos activos.

```bash
curl -s -X POST -H "x-api-key: 123456" http://localhost:8080/polizas/2/cancelar
```

### 5. Agregar un riesgo

Solo en pólizas colectivas. El body es **obligatorio**; los tres campos no pueden
estar vacíos. Responde 201 con el riesgo creado, incluido su id.

```bash
curl -s -X POST -H "x-api-key: 123456" -H "Content-Type: application/json" \
  -d '{
        "direccionInmueble": "Transversal 12 # 50-60, Cali",
        "arrendatario": "Sofia Pena",
        "arrendador": "Inmobiliaria Andina SAS"
      }' \
  http://localhost:8080/polizas/2/riesgos
```

### 6. Cancelar un riesgo

```bash
curl -s -X POST -H "x-api-key: 123456" http://localhost:8080/riesgos/2/cancelar
```

### 7. Mock del CORE

Representa el sistema externo. No persiste nada: valida el body, registra la
recepción en el log y responde 202.

```bash
curl -s -X POST -H "x-api-key: 123456" -H "Content-Type: application/json" \
  -d '{"evento":"ACTUALIZACION","polizaId":555}' \
  http://localhost:8080/core-mock/evento
```

```json
{ "recibido": true }
```

Cada operación que cambia estado deja una línea en el log:

```bash
docker compose logs app | grep "\[CORE-MOCK\]"
# [CORE-MOCK] Se intentó enviar al CORE: evento=ACTUALIZACION polizaId=2
```

---

## Notificaciones (simuladas)

Cuando algo relevante le pasa a su póliza, al tomador se le avisaría por correo y
SMS. Esa parte no está construida: lo que hay es el **mecanismo** que la
disparará, con el envío simulado por una línea de log.

### Qué notifica y qué no

| Operación | ¿Notifica? | Por qué |
|---|---|---|
| Renovar póliza | **Sí** | Cambian el canon, la prima y la vigencia: el tomador tiene que saber cuánto y hasta cuándo va a pagar. |
| Agregar riesgo | **Sí** | Entra un inmueble nuevo a la cobertura, y conviene que quede constancia ante el tomador. |
| Cancelar póliza | No | Hoy no se notifica. La cancelación nace de una decisión del propio tomador o del asesor, así que el aviso sería redundante; si el negocio pidiera una carta de terminación, se añade un `PolizaCanceladaEvent` y un listener, sin tocar el servicio. |
| Cancelar riesgo | No | Mismo razonamiento: sale un inmueble de la cobertura por una decisión ya conocida, y la póliza sigue vigente (R8). |

### Cómo funciona

Los servicios, después de guardar, publican un evento de dominio:

```java
publicadorDeEventos.publishEvent(PolizaRenovadaEvent.de(guardada));        // renovar
publicadorDeEventos.publishEvent(RiesgoAgregadoEvent.de(poliza, guardado)); // agregar riesgo
```

Los eventos son `record` con los datos del cambio: `PolizaRenovadaEvent` lleva id,
número, tomador, canon mensual, prima y fecha de fin de vigencia;
`RiesgoAgregadoEvent` lleva id y número de la póliza, tomador, id del riesgo y
dirección del inmueble. Ambos llevan los valores **copiados** y no una referencia
a la entidad, por dos razones: el consumidor corre cuando la sesión de
persistencia ya se cerró, y un evento debe describir lo que pasó en el momento en
que pasó, no el estado actual de la fila.

`NotificacionListener` los consume, un método por evento:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void alRenovarPoliza(PolizaRenovadaEvent evento) { ... }

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void alAgregarRiesgo(RiesgoAgregadoEvent evento) { ... }
```

```bash
docker compose logs app | grep "\[NOTIFICACION\]"
# [NOTIFICACION] Se enviaría correo y SMS al tomador Ana Gomez por renovación
# de la póliza POL-IND-001 (nuevo canon 1893600.00, vigente hasta 2028-01-01)
# [NOTIFICACION] Se enviaría correo y SMS al tomador Inmobiliaria Andina SAS por
# la inclusión de un riesgo (Transversal 12 # 50-60, Cali) en la póliza POL-COL-001
```

Tres decisiones que vale señalar:

**La fase `AFTER_COMMIT` no es un detalle.** El listener solo corre si la
transacción de la operación se confirmó. Si la operación se deshiciera, no se
avisaría al tomador de un cambio que nunca ocurrió, que es el error clásico de
notificar dentro de la transacción.

**Un fallo al notificar no afecta a la operación.** El envío va envuelto en un
`try/catch` que registra el fallo con `log.error`. Y aunque no lo hiciera, Spring
no propaga las excepciones de un listener `AFTER_COMMIT`: la petición sigue
respondiendo igual y el cambio queda confirmado. Hay una prueba que lo comprueba
registrando un listener que falla a propósito, sin red de seguridad.

**Solo se publica en operaciones exitosas.** Un 404, un 409 o un 400 lanzan antes
de llegar a la publicación, así que no se emite ningún evento.

### Por qué un evento y no una llamada directa

Los servicios no conocen al listener: publican un hecho y se desentienden de
quién lo escucha. Agregar un canal nuevo —una notificación al asesor, una
métrica, una entrada de auditoría— es añadir un listener, sin tocar la lógica de
negocio. Y notificar una operación más es publicar un evento más, como muestra el
alta de riesgos.

### Evolución

| Hoy | Siguiente paso |
|---|---|
| Evento en memoria, dentro del mismo proceso | **Outbox**: guardar el evento en la misma transacción que el cambio, y publicarlo después con reintentos. Sobrevive a una caída entre el commit y el envío. |
| Listener sincrónico en el hilo de la petición | **Broker** (Kafka, RabbitMQ, SQS): el consumidor procesa a su ritmo y la API no espera. |
| Línea de log | **Servicio de correo y SMS** detrás de su propio puerto de salida, igual que `CoreGateway`, con plantillas y control de reintentos. |

El mismo mecanismo cubre el resto del ciclo de vida: la creación de pólizas
publicaría un `PolizaCreadaEvent` con su propio listener de bienvenida, y la
cancelación un `PolizaCanceladaEvent` si el negocio pidiera una carta de
terminación. Por eso el consumidor está en un paquete propio, `notificacion`, y
no colgado del paquete `poliza`.

---

## Reglas de negocio

Viven en métodos de las entidades (`Poliza.renovar`, `Poliza.cancelar`,
`Poliza.agregarRiesgo`, `Riesgo.cancelar`), no en los servicios.

| # | Regla | Si se viola |
|---|---|---|
| R1 | Agregar riesgo solo en póliza `COLECTIVA`. Una individual tiene exactamente un riesgo y no admite más | 409 |
| R2 | No se agregan riesgos a una póliza `CANCELADA` | 409 |
| R3 | No se puede renovar una póliza `CANCELADA` | 409 |
| R4 | Una póliza `VIGENTE` o `RENOVADA` sí puede renovarse, y varias veces | — |
| R5 | Cancelar una póliza cancela todos sus riesgos `ACTIVO` | — |
| R6 | Cancelar una póliza ya `CANCELADA` | 409 |
| R7 | Cancelar un riesgo ya `CANCELADO` | 409 |
| R8 | Cancelar un riesgo no cambia el estado de la póliza, ni en individuales ni en colectivas | — |

### Códigos de error

Los errores se devuelven como `ProblemDetail` (RFC 9457), con
`Content-Type: application/problem+json`.

| Código | Situación |
|---|---|
| **401** | Falta el header `x-api-key` o su valor no coincide |
| **404** | La póliza o el riesgo no existe |
| **409** | Se violó una regla de negocio (R1, R2, R3, R6, R7) |
| **400** | Body inválido o ausente donde es obligatorio, JSON malformado, `ipc` fuera del rango 0..100, `tipo` o `estado` con un valor que no existe, id no numérico |

```json
{
  "status": 409,
  "title": "Regla de negocio violada",
  "detail": "No se puede renovar la poliza POL-COL-002 porque esta CANCELADA"
}
```

---

## Arquitectura

```
com.davivienda.demoti
├── DemoTiApplication
├── poliza
│   ├── controller      PolizaController, RiesgoController
│   ├── service         PolizaService, RiesgoService
│   ├── repository      PolizaRepository, RiesgoRepository
│   ├── domain          Poliza, Riesgo, TipoPoliza, EstadoPoliza, EstadoRiesgo
│   └── dto             PolizaResponse, RiesgoResponse, RenovarRequest, CrearRiesgoRequest
├── notificacion
│   ├── PolizaRenovadaEvent     evento de dominio
│   ├── RiesgoAgregadoEvent     evento de dominio
│   └── NotificacionListener    consumidor, en AFTER_COMMIT
├── core
│   ├── CoreGateway             puerto de salida
│   ├── CoreGatewayHttp         adaptador HTTP
│   ├── CoreMockController      POST /core-mock/evento
│   └── EventoCoreRequest
└── shared
    ├── security        ApiKeyFilter, SecurityConfig
    ├── exception       ReglaNegocioException, RecursoNoEncontradoException, GlobalExceptionHandler
    └── seed            DataSeeder
```

**Reparto de responsabilidades.** Las reglas de negocio están en las entidades;
los servicios solo orquestan, en un orden fijo: cargar (404 si no existe),
invocar la regla de dominio (409 si se viola), guardar y notificar al CORE. Los
controladores no contienen lógica.

**`CoreGateway` es un puerto de salida.** Los servicios dependen de la interfaz y
nunca de HTTP, así que el transporte se puede cambiar por una cola, un outbox u
otro protocolo sin tocar la lógica de negocio. `CoreGatewayHttp` es el único
adaptador, y `CoreMockController` representa al CORE: en producción ese endpoint
estaría en otro sistema y solo cambiaría `core.base-url`. Por eso, dentro del
contenedor, `core.base-url=http://localhost:8080` hace que la aplicación se llame
a sí misma.

**Asignación de rutas.** `PolizaController` atiende todo lo que cuelga de
`/polizas`, incluido el alta de riesgos, porque un riesgo no existe sin su
póliza; la orquestación de ese alta es de `RiesgoService`. `RiesgoController`
atiende `/riesgos/{id}/cancelar`, donde el riesgo se identifica por sí mismo.

**Entidades JPA en `domain`: una concesión pragmática.** `Poliza` y `Riesgo` son
a la vez el modelo de dominio y las entidades persistentes, con anotaciones de JPA
en la misma clase. Lo correcto en un diseño hexagonal estricto sería separar el
modelo de dominio de las entidades de persistencia y mapear entre ambos, pero eso
duplica clases y añade mappers sin aportar nada en un alcance de este tamaño. Las
precauciones que sí se tomaron: no hay setters (el estado solo cambia por métodos
de negocio), `getRiesgos()` devuelve una lista inmodificable, y las entidades
**nunca** salen por la API: todas las respuestas son `record` de DTO.

**`open-in-view` está desactivado**, así que la sesión se cierra al salir del
servicio y el mapeo entidad → DTO se hace allí dentro, bajo `@Transactional`
(`readOnly = true` en las lecturas). `PolizaResponse` no incluye los riesgos, de
modo que listar pólizas no toca la colección *lazy*: es una sola consulta. Los
riesgos de una póliza se traen con un `join fetch`, lo que deja el endpoint en dos
consultas constantes, sin importar cuántos riesgos tenga.

---

## Supuestos

El enunciado no define estos puntos; se resolvieron así.

**IPC y renovación**
- El IPC se expresa en **porcentaje**: `5.2` significa 5,2 %.
- Valor por defecto en `polizas.ipc = 5.2`, sobrescribible por petición con
  `{"ipc": 6.1}` en el body. El rango válido es 0 a 100; fuera de él, 400.
- La vigencia se **corre un periodo**: la nueva fecha de inicio es la fecha de fin
  anterior, y la de fin se recalcula sumando los mismos meses de vigencia.
- La prima se deriva: `prima = canon nuevo × meses de vigencia`.
- El cálculo se hace como `canon × (100 + ipc) / 100`, que es la misma fórmula que
  `canon × (1 + ipc/100)` pero redondea una sola vez, a escala 2 con `HALF_UP`.
- Las **renovaciones sucesivas están permitidas** (R4) y cada una se aplica sobre
  el canon ya renovado, no sobre el original: 1000.00 → 1052.00 → 1106.70.

**Modelo**
- El **canon vive en la póliza**, no en cada riesgo. Es una simplificación: en un
  modelo real cada inmueble tendría su propio canon.
- **Toda póliza nace con su primer riesgo**, a través del constructor. Es la única
  forma de que una póliza individual tenga riesgo, porque R1 prohíbe agregarle
  riesgos, y convierte "una póliza cubre al menos un inmueble" en una invariante.
- **R1 se implementó de forma literal**: si el tipo no es `COLECTIVA`, el alta se
  rechaza con 409, sin excepciones. La regla admitía leerse como "permitir el
  primer riesgo si no tiene ninguno", pero su enunciado es incondicional.

**Operaciones repetidas**
- Cancelar algo ya cancelado es un **409**, no una operación idempotente que
  devuelva 200. Se prefiere avisar al cliente de que su suposición sobre el estado
  era incorrecta.

**Integración con el CORE**
- La notificación ocurre **después de guardar** y es la última acción de la
  operación. Si la regla de negocio falla, el CORE no se invoca.
- **Un fallo del CORE no revierte la operación**: el adaptador captura la
  excepción y la registra con `log.error`. La consecuencia asumida es que un
  evento se puede perder si el CORE está caído; queda en el log para reenviarlo.
  La evolución natural es el patrón **outbox**: guardar el evento en la misma
  transacción que el cambio y publicarlo después con reintentos.
- El CORE se notifica siempre con el id de la **póliza**, también al cancelar un
  riesgo: lo que cambia desde su punto de vista es la póliza.

**Infraestructura**
- **Sin Flyway ni Liquibase**, por el tiempo disponible para la prueba: el esquema
  lo genera `spring.jpa.hibernate.ddl-auto=update`. En un proyecto real las
  migraciones versionadas son obligatorias; esto es lo primero que habría que
  cambiar.
- Los datos de ejemplo los carga un `DataSeeder` (`CommandLineRunner`) y no un
  `data.sql`, para que sean idempotentes y para que no se ejecuten en los tests,
  donde cada caso construye y limpia sus propios datos.
- La API key viaja en un header y se compara en tiempo constante
  (`MessageDigest.isEqual`). El 401 no distingue entre header ausente y clave
  inválida, y el valor recibido nunca se escribe en el log.

---

## Matriz de trazabilidad

Las 36 filas de la matriz de casos tienen al menos una prueba. Los IDs aparecen en
el `@DisplayName` de cada prueba, así que se pueden localizar con
`grep -rn "CU3-c" src/test`.

| Caso | Descripción | Clases de prueba |
|---|---|---|
| CU1-a | Listar sin filtros | `PolizaControllerIT` |
| CU1-b | Listar por tipo | `PolizaControllerIT` |
| CU1-c | Listar por estado | `PolizaControllerIT` |
| CU1-d | Listar por tipo y estado | `PolizaControllerIT` |
| CU1-e | Filtro con valor inválido → 400 | `PolizaControllerIT` |
| CU2-a | Riesgos de una póliza existente | `PolizaControllerIT` |
| CU2-b | Riesgos de una póliza inexistente → 404 | `PolizaControllerIT` |
| CU3-a | Renovar con el IPC por defecto | `PolizaTest`, `PolizaControllerIT` |
| CU3-b | Renovar con `ipc` en el body | `PolizaServiceTest`, `PolizaControllerIT` |
| CU3-c | Renovar una póliza cancelada → 409, sin CORE | `PolizaTest`, `PolizaServiceTest`, `PolizaControllerIT` |
| CU3-d | Renovar una póliza ya renovada | `PolizaTest`, `PolizaControllerIT` |
| CU3-e | Redondeo a escala 2 HALF_UP | `PolizaTest`, `PolizaControllerIT` |
| CU3-f | `ipc` fuera de rango → 400 | `PolizaControllerIT` |
| CU3-g | Renovar una póliza inexistente → 404 | `PolizaServiceTest`, `PolizaControllerIT` |
| CU4-a | Cancelar póliza y sus riesgos, CORE una vez | `PolizaTest`, `PolizaServiceTest`, `PolizaControllerIT` |
| CU4-b | Cancelar una póliza ya cancelada → 409 | `PolizaTest`, `PolizaServiceTest`, `PolizaControllerIT` |
| CU4-c | Cancelar una póliza inexistente → 404 | `PolizaServiceTest`, `PolizaControllerIT` |
| CU5-a | Agregar riesgo a una colectiva → 201 | `PolizaTest`, `RiesgoServiceTest`, `PolizaControllerIT` |
| CU5-b | Agregar riesgo a una individual → 409 | `PolizaTest`, `RiesgoServiceTest`, `PolizaControllerIT` |
| CU5-c | Agregar riesgo a una póliza cancelada → 409 | `PolizaTest`, `RiesgoServiceTest`, `PolizaControllerIT` |
| CU5-d | Body inválido → 400 | `PolizaControllerIT` |
| CU5-e | Póliza inexistente → 404 | `RiesgoServiceTest`, `PolizaControllerIT` |
| CU6-a | Cancelar riesgo, póliza sin cambios (R8) | `RiesgoTest`, `RiesgoServiceTest`, `RiesgoControllerIT` |
| CU6-b | Cancelar un riesgo ya cancelado → 409 | `RiesgoTest`, `RiesgoServiceTest`, `RiesgoControllerIT` |
| CU6-c | Cancelar un riesgo inexistente → 404 | `RiesgoServiceTest`, `RiesgoControllerIT` |
| CU7-a | Petición sin `x-api-key` → 401 | `ApiKeyFilterIT` |
| CU7-b | Petición con clave incorrecta → 401 | `ApiKeyFilterIT` |
| CU7-c | Petición con clave correcta → 200 | `ApiKeyFilterIT` |
| CU8-a | Mock del CORE con body válido → 202 | `CoreMockControllerIT` |
| CU8-b | Mock del CORE con body inválido → 400 | `CoreMockControllerIT` |
| CU8-c | Un fallo del CORE no se propaga | `CoreGatewayHttpTest` |
| CU9-a | Renovar publica el evento y notifica al tomador | `PolizaServiceTest`, `NotificacionListenerIT` |
| CU9-b | Una renovación fallida no notifica | `PolizaServiceTest`, `NotificacionListenerIT` |
| CU9-c | Un fallo al notificar no afecta a la renovación | `NotificacionConFalloIT` |
| CU9-d | Agregar un riesgo publica el evento y notifica al tomador | `RiesgoServiceTest`, `NotificacionListenerIT` |
| CU9-e | Un alta fallida, y las cancelaciones, no notifican | `RiesgoServiceTest`, `NotificacionListenerIT` |

### Niveles de prueba

| Nivel | Clases | Qué cubre |
|---|---|---|
| Dominio (JUnit puro) | `PolizaTest`, `RiesgoTest` | Reglas y cálculos, sin Spring ni base de datos |
| Servicio (Mockito) | `PolizaServiceTest`, `RiesgoServiceTest` | Orquestación, 404, y que el CORE se invoque **solo** cuando la operación tiene éxito |
| Unitaria | `CoreGatewayHttpTest` | Que un fallo del CORE no se propague y quede registrado |
| Integración (Testcontainers) | `PolizaControllerIT`, `RiesgoControllerIT`, `CoreMockControllerIT`, `ApiKeyFilterIT`, `NotificacionListenerIT`, `NotificacionConFalloIT` | Filtro → controlador → servicio → PostgreSQL real |

Los tests de integración reemplazan `CoreGateway` por un mock, porque MockMvc no
abre un puerto real y el adaptador HTTP no podría llamarse a sí mismo. Cada clase
construye sus propios datos con los repositorios y los limpia en `@BeforeEach`.

---

## Fuera de alcance

Decisiones conscientes, no omisiones:

| Qué | Por qué y cómo evolucionaría |
|---|---|
| **Outbox** | Tanto la notificación al CORE como el evento de renovación son *best effort*: un fallo pierde el aviso. El outbox los guardaría en la misma transacción que el cambio de estado y los publicaría después con reintentos y garantía de entrega al menos una vez. |
| **Broker de mensajería** | Hoy la integración es HTTP sincrónico. Con un broker el CORE consumiría eventos a su ritmo y la API dejaría de depender de su disponibilidad. |
| **JWT / OAuth2** | La API key es suficiente para una integración interna, pero no identifica a quien llama ni expira. La autenticación real corresponde al API Gateway, que validaría un JWT y propagaría la identidad. |
| **Flyway / Liquibase** | `ddl-auto=update` sirve para una prueba, no para producción: no versiona el esquema ni permite revisar o revertir un cambio. Migraciones versionadas desde el primer despliegue. |
| **Paginación** | `GET /polizas` devuelve la lista completa. Con volumen real haría falta paginación y ordenación por parámetro. |
| **Mappers (MapStruct)** | El mapeo entidad → DTO se hace a mano en métodos de fábrica de los `record`. Con más DTOs, un mapper generado evitaría el código repetitivo. |
