# Kestra Gemini Plugin

## What

- Provides plugin components under `io.kestra.plugin.gemini`.
- Includes classes such as `VideoGeneration`, `MultimodalCompletion`, `TextCompletion`, `StructuredOutputCompletion`.

## Why

- What user problem does this solve? Teams need to call Google Gemini models for text, chat, structured outputs, multimodal prompts, and video generation from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Gemini steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Gemini.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `gemini`

### Key Plugin Classes

- `io.kestra.plugin.gemini.ChatCompletion`
- `io.kestra.plugin.gemini.MultimodalCompletion`
- `io.kestra.plugin.gemini.StructuredOutputCompletion`
- `io.kestra.plugin.gemini.TextCompletion`
- `io.kestra.plugin.gemini.VideoGeneration`

### Project Structure

```
plugin-gemini/
├── src/main/java/io/kestra/plugin/gemini/
├── src/test/java/io/kestra/plugin/gemini/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
