# sharedCore.ai

`AiProvider` defines the platform-neutral contract for a chat provider. It exposes provider identity, normal chat, streaming chat, and streaming capability.

`AiProviderRegistry` owns provider registration and lookup. It registers and removes providers, returns providers by id, exposes all providers, tracks a default provider, and answers containment checks.

`AiEngine` is the provider-neutral execution layer. It delegates chat, stream, and streaming capability checks through `AiProviderRegistry` and contains no provider-specific logic.
