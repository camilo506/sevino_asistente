# Sevino Asistente

Mod de Minecraft Java **1.20.1** (Forge **47.4.0**) que agrega un asistente NPC inteligente
("Sevino") que acompaña al jugador y le ayuda a aprender inglés mientras juega.

El asistente funciona conectándose a un servidor **Ollama** local en tu PC, así que **no
necesita internet** para conversar (solo para descargar el modelo la primera vez).

## Características

- NPC humanoide que sigue al jugador y se teletransporta si te alejas mucho.
- Conversación libre desde el chat con el prefijo `!ai`.
- Traducción rápida con `!tr` o `/sevino tr`.
- Corrección de frases en inglés con `!en` o `/sevino fix`.
- Niveles de inglés configurables: `beginner`, `intermediate`, `advanced`.
- Tarjetas de vocabulario por tema (`/sevino vocab mining`, `farming`, `food`, etc).
- Mini-misiones generadas por la IA (`/sevino quest`) con tracking automático
  para misiones del tipo `COLLECT N item`.
- Contexto del juego enviado al modelo: bioma, hora, clima, HP, hambre, hotbar y
  bloque al que mira el jugador, para que el asistente pueda enseñar vocabulario
  basado en lo que ves.
- Toda la respuesta del asistente viene en **inglés / español** para que aprendas
  comparando.

## Requisitos

1. **Java 17** instalado.
2. **Minecraft Java Edition 1.20.1** con **Forge 47.4.0** (o más reciente de la rama 1.20.1).
3. **Ollama** corriendo en tu PC: <https://ollama.com/download>

## Configurar Ollama (una sola vez)

```bash
ollama pull llama3.2
ollama serve
```

`ollama serve` debe quedar corriendo en segundo plano cuando juegues.
Si ya tienes el cliente de Ollama abierto, normalmente el servidor está corriendo
en `http://localhost:11434` automáticamente.

Puedes probar que funciona con:

```bash
curl http://localhost:11434/api/tags
```

## Compilar el mod

En la carpeta del proyecto, abre una terminal y ejecuta:

```bash
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

El `.jar` final aparecerá en `build/libs/sevinoasistente-0.1.0.jar`.

Cópialo a la carpeta `mods/` de tu instalación de Minecraft Forge 1.20.1.

## Probar en desarrollo

```bash
# Lanzar cliente de desarrollo
gradlew.bat runClient
```

Esto abre Minecraft con Forge y el mod ya cargado, y te deja entrar a un mundo de prueba.

## Comandos en juego

| Comando | Qué hace |
|---|---|
| `/sevino spawn` | Invoca al asistente Sevino al lado tuyo y se vincula como tu acompañante. |
| `/sevino despawn` | Elimina al asistente vinculado. |
| `/sevino tr <texto>` | Traduce el texto (auto-detecta inglés o español). |
| `/sevino fix <frase>` | Corrige una frase en inglés y explica el error en español. |
| `/sevino level <beginner\|intermediate\|advanced>` | Cambia el nivel de inglés del jugador. |
| `/sevino vocab` | Te da una palabra de vocabulario al azar. |
| `/sevino vocab <tema>` | Vocabulario del tema (mining, farming, food, animals, weather, etc). |
| `/sevino quest` | Genera una mini-misión en inglés. Si es de tipo `COLLECT`, se trackea automáticamente. |
| `/sevino reset` | Borra el historial de la conversación con el asistente. |

`/ai` es un alias corto de `/sevino`.

## Prefijos de chat

Sin necesidad de comandos, puedes escribir directamente en el chat:

- `!ai how do I find diamonds?` → conversación libre con el asistente.
- `!tr murciélago` → traducción rápida.
- `!en I has a sword` → corrección gramatical.

(Los prefijos son configurables en el archivo de configuración).

## Configuración

El archivo se genera en `config/sevinoasistente-common.toml` la primera vez que
ejecutas el mod. Allí puedes cambiar:

```toml
[ollama]
url = "http://localhost:11434"
model = "llama3.2"
timeoutSeconds = 60
maxTokens = 256
temperature = 0.7

[chat]
prefix = "!ai"
translatePrefix = "!tr"
correctPrefix = "!en"

[assistant]
englishLevel = "beginner"
includeGameContext = true
allowAutoCorrect = true
vocabTopics = ["mining", "farming", "animals", "food", "weather",
               "directions", "tools", "combat", "building"]
```

## Cambiar la skin del asistente

La textura del asistente está en
`src/main/resources/assets/sevinoasistente/textures/entity/assistant.png`.

Es un PNG **64x64** con el layout de skin estándar de Minecraft (Steve), así que
puedes editarlo en cualquier editor de skins online y reemplazar el archivo.

## Estructura del proyecto

```
src/main/java/com/sevino/asistente/
  SevinoAsistente.java          Clase principal, registros y eventos del mod bus
  config/AsistenteConfig.java   Configuración Forge (ollama, prefijos, nivel, etc.)
  ollama/OllamaClient.java      Cliente HTTP async hacia /api/chat de Ollama
  ollama/PromptBuilder.java     Prompts (system + contexto del juego)
  chat/ChatHistory.java         Historial corto por jugador
  chat/AssistantConversation.java  Orquestación: prompt + Ollama + envío al jugador
  event/ChatPrefixHandler.java  Intercepta !ai / !tr / !en del chat
  event/PlayerEvents.java       Tick de misiones y limpieza al desconectar
  command/SevinoCommands.java   Comandos /sevino y /ai
  entity/ModEntities.java       Registro DeferredRegister
  entity/AssistantEntity.java   Entidad NPC pasiva con dueño
  entity/FollowOwnerGoal.java   Goal de seguir al dueño + teleport
  client/AssistantModel.java    HumanoidModel del asistente
  client/AssistantRenderer.java Renderer humanoide con armadura
  client/ModModelLayers.java    ModelLayerLocation
  quest/QuestManager.java       Generación y tracking de misiones COLLECT
```

## Solución de problemas

- **`[Ollama] No se pudo conectar`**: Asegúrate de que `ollama serve` está
  corriendo y la URL en la config coincide.
- **Tarda mucho en responder**: Baja `maxTokens` o usa un modelo más pequeño
  (`ollama pull tinyllama`, luego cambia `model = "tinyllama"` en la config).
- **El asistente no me sigue**: Haz click derecho sobre él para vincularlo como
  dueño, o usa `/sevino spawn` para invocar uno nuevo que ya quede vinculado.
- **Quiero más contexto del juego**: Asegúrate de tener `includeGameContext = true`
  en el archivo de configuración.

## Licencia

MIT
