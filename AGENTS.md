# Kestra Gemini Plugin

## What

- Provides plugin components under `io.kestra.plugin.gemini`.
- Includes classes such as `VideoGeneration`, `MultimodalCompletion`, `TextCompletion`, `StructuredOutputCompletion`.

## Why

- This plugin integrates Kestra with Gemini.
- It provides tasks that call Google Gemini models for text, chat, structured outputs, multimodal prompts, and video generation.

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
