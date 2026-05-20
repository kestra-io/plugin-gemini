# How to use the Gemini plugin

Call Google Gemini models for text, chat, multimodal, structured output, and video generation tasks.

## Authentication

Set `apiKey` to your Gemini API key. Store it in a [secret](https://kestra.io/docs/concepts/secret).

## Tasks

Choose the task that matches your input and output shape. `TextCompletion` sends a single text prompt and returns a response — use it for simple, one-shot generation. `ChatCompletion` supports multi-turn conversations with a message history. `MultimodalCompletion` accepts both text and images in the same prompt, making it the right choice when your input includes visual data.

`StructuredOutputCompletion` returns a JSON response conforming to a schema you define — use it when downstream tasks need a predictable data structure rather than free-form text. `VideoGeneration` generates a video from a text description and is the only task that does not return text.
