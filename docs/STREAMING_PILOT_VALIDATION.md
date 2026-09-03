# Streaming Pilot Validation

## Streaming-Lebenszyklus

Der Android-Streaming-Pilot laeuft ueber `AndroidAiOrchestrator.streamEvents`.

1. Der Pilot startet nur, wenn Developer Mode, Shared-AI-Experimental und Shared-AI-Streaming-Pilot aktiv sind.
2. Bei deaktiviertem Flag wird sofort der Legacy-Stream genutzt.
3. Bei aktivem Flag wird der Shared-AI-Provider fuer OpenRouter instanziiert und der Stream vollstaendig gesammelt.
4. Vor einem Erfolg werden drei Bedingungen validiert:
   - mindestens ein nichtleeres Delta
   - ein `AiStreamCompleted` Event
   - finaler Antworttext ist nicht leer
5. Bei Providerfehlern, leerem Stream oder unvollstaendiger Antwort wird sofort auf Legacy zurueckgefallen.
6. Cancellation wird nicht geschluckt und fuehrt nicht zu Legacy-Fallback.

Es werden keine Prompt-, Nachrichten- oder Antwortinhalte in Telemetry geschrieben.

## Telemetry-Events

`stream_pilot_attempt`

- `provider`
- `model`

`stream_pilot_success`

- `provider`
- `model`
- `duration_ms`
- `stream_duration_ms`
- `delta_count`
- `final_length`
- `final_text_length`

`stream_pilot_error`

- `provider`
- `model`
- `exception`
- `duration_ms`
- `stream_duration_ms` bei stream-internen Fehler-Events
- `delta_count` bei stream-internen Fehler-Events
- `final_length` bei stream-internen Fehler-Events
- `final_text_length` bei stream-internen Fehler-Events

`stream_pilot_fallback`

- `provider`
- `model`
- `reason`
- `fallback_reason`

## Fallback-Gruende

- `flag_disabled`: Streaming-Pilot ist nicht aktiv.
- `provider_error`: Shared-AI-Stream oder Provider meldet einen Fehler.
- `empty_response`: kein nichtleeres Delta, kein Completed Event oder leerer finaler Text.
- `legacy_exception`: Legacy-Fallback wurde aufgerufen, hat aber selbst eine Exception geworfen.

## Geraetetest

Empfohlener Ablauf nach Installation eines Debug-Builds:

1. App-Daten bei Bedarf zuruecksetzen.
2. Developer Mode aktivieren.
3. Shared-AI-Experimental aktivieren.
4. Shared-AI-Streaming-Pilot aktivieren.
5. OpenRouter mit gueltigem Key auswaehlen.
6. Eine kurze Chat-Nachricht senden.
7. Logcat auf Pilot-Events pruefen.

## ADB-Flags

Debug-Builds koennen den Pilot per Broadcast schalten:

```powershell
adb shell am broadcast -a com.example.bamachat.debug.SET_SHARED_AI_PILOT --ez enabled true --ez streaming_enabled true
adb shell am broadcast -a com.example.bamachat.debug.SET_SHARED_AI_PILOT --ez enabled false --ez streaming_enabled false
```

## Logcat-Filter

```powershell
adb logcat | Select-String "stream_pilot"
adb logcat | Select-String "AppTelemetry|stream_pilot"
```

## Erwartete Erfolgslogs

Ein erfolgreicher Pilot-Turn zeigt mindestens:

```text
stream_pilot_attempt provider=OPENROUTER
stream_pilot_success provider=OPENROUTER duration_ms=<n> delta_count=<n> final_length=<n>
```

Ein gueltiger Fallback zeigt:

```text
stream_pilot_fallback provider=OPENROUTER fallback_reason=provider_error
stream_pilot_fallback provider=OPENROUTER fallback_reason=empty_response
stream_pilot_fallback provider=OPENROUTER fallback_reason=flag_disabled
stream_pilot_fallback provider=OPENROUTER fallback_reason=legacy_exception
```
