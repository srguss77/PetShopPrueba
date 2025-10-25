# Bot Prompts (Gemini)

Orden de uso en la app:

1) SYSTEM (cargar en systemInstruction):
   - 01_system_prompt_es.txt
   - Opcional: añade contexto dinámico (ciudad, refugio) como variables.

2) INTENT CLASSIFIER (llamada previa, barata y corta):
   - 03_intent_classifier_prompt.txt
   - Envía el mensaje del usuario y espera JSON: {"intent":"ADOPCION", "confidence":0.92, "reason":"..."}
   - Usa el intent para activar respuestas más precisas y/o UI (sugerencias, checklists).

3) EMERGENCY CHECK (guardarraíl rápido):
   - 04_emergency_and_safety_prompt.txt
   - Corre antes de responder. Si devuelve "EMERGENCY", muestra aviso y deriva a vet.

4) GENERACIÓN DE RESPUESTA:
   - Usa el SYSTEM + 02_response_template.txt como recordatorio de formato.
   - Puedes inyectar 05_few_shot_examples.json como mensajes de ejemplo.

5) UI/Onboarding:
   - 06_onboarding_message.txt para el primer mensaje del bot.
   - 07_short_mobile_prompt.txt si necesitas una versión ultra-corta del SYSTEM (dispositivos con espacio limitado).

Sugerencias:
- Mantén respuestas en 2–3 párrafos + viñetas + pregunta final.
- No des dosis ni diagnósticos. Deriva emergencias.
- Contexto local: Guatemala (clima, disponibilidad de servicios, refugios).
