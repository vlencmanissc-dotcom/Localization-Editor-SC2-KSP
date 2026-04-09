README_TRANSLATE.txt
Last updated: 2026-04-09

# Translation Backends Guide

This file explains every translation service currently supported by Localization Editor SC2 KSP.

It is written for real project use, not just for API documentation. The goal is to help you choose the best backend for your workflow, budget, translation quality needs, and setup complexity.

Important notes:
- Prices, free tiers, and limits may change over time. Always double-check the official provider pages before paying.
- Backend names in this file match the names shown in the application UI.
- LibreTranslate in this app is local-only. Hosted or cloud LibreTranslate endpoints are not supported by the current in-app integration.

## Backends available in the app

- `LibreTranslate AI`
- `Google Cloud AI`
- `Cloudflare Worker AI (M2M100)`
- `Google Translate Free (Web)`
- `Gemini Vertex AI (2.5 Flash-Lite)`
- `SiliconFlow AI (Free Models)`
- `SiliconFlow AI (DeepSeek-V3.2)`
- `DeepL API Free`

## Quick recommendations

- Best official free option: `DeepL API Free`
- Best official high-speed option for large projects: `Google Cloud AI`
- Easiest no-key option: `Google Translate Free (Web)`
- Best context-aware LLM options: `Gemini Vertex AI` or `SiliconFlow AI (DeepSeek-V3.2)`
- Cheapest cloud backend for bulk translation: `Cloudflare Worker AI (M2M100)`
- Best offline / self-hosted option: `LibreTranslate AI`

---

## 1. LibreTranslate AI

### What it is

`LibreTranslate AI` in this application is a local backend. It is designed to run on your machine, typically through Docker or a local Python setup.

This app does **not** currently support hosted or cloud LibreTranslate accounts as a built-in remote translation backend. In other words:

- local LibreTranslate: supported
- hosted/cloud LibreTranslate API key services: not supported by this app integration

### Advantages

- No external cloud API key is required
- Can run locally on your own machine
- Good for offline or self-hosted usage
- Useful if you do not want to send text to third-party cloud LLM services

### Disadvantages and practical notes

- Translation quality is usually below DeepL, Google Cloud, Gemini, or strong LLM-based services
- First startup can be slow because the local service must be launched
- Best treated as a local convenience backend, not a top-quality translation backend

### Free tier

- Local usage on your own machine: free, except for your own hardware and power usage
- Hosted LibreTranslate plans are separate paid products and are not used by this app

### Paid pricing

For the app's supported local mode: `0`

If you want to look at LibreTranslate hosted pricing anyway, their public portal currently lists:
- `Pro`: `$29 / month`
- `Business`: `$58 / month`

Again, those hosted plans are not directly integrated into this app.

### Where to get it

- Main site: https://libretranslate.com/
- API docs: https://libretranslate.com/docs/
- Hosted portal: https://portal.libretranslate.com/
- GitHub: https://github.com/LibreTranslate/LibreTranslate

### How to use it in this app

1. Select `LibreTranslate AI`
2. Let the app launch the local service
3. If available, Docker and GPU mode can improve local startup/runtime behavior

### Best use case

- Local-only setup
- No external API account
- Offline-friendly workflow

---

## 2. Google Cloud AI

### What it is

This backend uses Google Cloud Translation API. In this project it is implemented via Cloud Translation Basic v2.

### Advantages

- Very fast
- Official and stable API
- Excellent for large translation batches
- One of the best choices for production-scale bulk translation

### Disadvantages and practical notes

- Requires a Google Cloud account and API key
- Paid after the free monthly credit is exhausted
- Quality is generally strong, but DeepL may sound more natural on some language pairs

### Free tier

Google Cloud Translation Basic currently provides:
- first `500,000 characters per month` free as monthly credit

New Google Cloud users may also get:
- `$300` free trial credits

### Paid pricing

After the free monthly credit:
- `$20 per 1 million characters`

### Where to get it

- Pricing: https://cloud.google.com/translate/pricing
- Setup guide: https://docs.cloud.google.com/translate/docs/setup
- API key management: https://cloud.google.com/api-keys/docs/create-manage-api-keys
- Free trial: https://cloud.google.com/free
- Credentials page: https://console.cloud.google.com/apis/credentials

### How to get the API key

1. Create a Google Cloud account
2. Create a project
3. Enable `Cloud Translation API`
4. Open `APIs & Services > Credentials`
5. Create an API key
6. Paste it into the `Google Cloud API Key` field in the app

### Best use case

- Very large projects
- Fast official API
- Stable bulk translation workflow

---

## 3. Cloudflare Worker AI (M2M100)

### What it is

This backend uses the model `@cf/meta/m2m100-1.2b` through Cloudflare Workers AI.

It requires:
- `Cloudflare Account ID`
- `Cloudflare API Token`

### Advantages

- Very low cost
- Official cloud API
- Good choice for budget-sensitive large-scale translation
- Useful when cost matters more than maximum naturalness

### Disadvantages and practical notes

- Translation quality is usually below DeepL and strong LLM backends
- More mechanical output is possible
- Requires two credentials, not one
- Pricing is token-based, not a simple monthly character package

### Free tier

Workers AI is available on Cloudflare free and paid plans, but it is not a DeepL-style fixed free monthly translation package.

### Paid pricing

General Workers AI pricing:
- `$0.011 per 1,000 neurons`

For `@cf/meta/m2m100-1.2b`:
- `$0.342 per 1M input tokens`
- `$0.342 per 1M output tokens`

### Where to get it

- Pricing: https://developers.cloudflare.com/workers-ai/platform/pricing/
- REST API guide: https://developers.cloudflare.com/workers-ai/get-started/rest-api/
- Token creation docs: https://developers.cloudflare.com/fundamentals/api/get-started/create-token/
- Sign up: https://dash.cloudflare.com/sign-up/workers-and-pages

### How to get the credentials

1. Create a Cloudflare account
2. Open `Workers AI` in the Cloudflare dashboard
3. Click `Use REST API`
4. Create a `Workers AI API Token`
5. Copy the API token
6. Copy the Account ID
7. Paste both values into the app

### Best use case

- Very cost-sensitive bulk translation
- Large projects where quality is acceptable but budget is limited

---

## 4. Google Translate Free (Web)

### What it is

This backend uses the free Google Translate web path, not the official Google Cloud Translation paid API.

### Advantages

- No API key required
- Very easy to use
- Often very fast
- Great for quick draft translation

### Disadvantages and practical notes

- Not an official production API integration
- Behavior, limits, and reliability may change without notice
- Can be rate-limited or become unstable
- Best viewed as a convenience backend, not a guaranteed enterprise translation backend

### Free tier

- No separate API key required
- No direct payment required in this app mode

### Paid pricing

For this free web mode in the app: `0`

Official paid alternative:
- `Google Cloud AI`

### Where to get it

No signup is required for this backend.

If you want the official Google option instead:
- https://cloud.google.com/translate/pricing
- https://docs.cloud.google.com/translate/docs/setup

### Best use case

- Fast no-setup translation
- Draft pass
- Quick one-click workflow

---

## 5. Gemini Vertex AI (2.5 Flash-Lite)

### What it is

This backend uses Gemini through Vertex AI, not a simple web chat endpoint.

Current model used in the app:
- `gemini-2.5-flash-lite`

### Advantages

- Better context understanding than classic MT engines
- Useful for difficult, ambiguous, or game-specific strings
- Often better than standard translation engines on messy inputs

### Disadvantages and practical notes

- This is an LLM backend, not a classic translation engine
- It may rewrite phrasing more freely
- Not always the best choice for maximum throughput
- Express mode has request-per-minute limits
- Requires the correct Google / Vertex API key

### Free tier and limits

For new Google Cloud users:
- `90 days` free trial period

For `gemini-2.5-flash-lite` in express mode:
- `10 requests per minute`

Important nuance:
- Gemini free usage is not structured like DeepL's "500,000 characters per month"
- It is better understood as quota- and token-based usage
- In practice, Gemini behaves more like an LLM service with limits than a classic monthly translation package

### Paid pricing

For `Gemini 2.5 Flash Lite`:
- input: `$0.10 per 1M tokens`
- output: `$0.40 per 1M tokens`

### Where to get it

- Express mode overview: https://docs.cloud.google.com/vertex-ai/generative-ai/docs/start/express-mode/overview
- API key guide: https://docs.cloud.google.com/vertex-ai/generative-ai/docs/start/api-keys
- Pricing: https://cloud.google.com/vertex-ai/generative-ai/pricing
- Credentials page: https://console.cloud.google.com/apis/credentials

### How to get the API key

1. Open Google Cloud
2. Go to Vertex AI / express mode
3. Create or retrieve an API key
4. Make sure it is valid for Vertex AI API usage
5. Paste it into the `Gemini API Key` field in the app

### Best use case

- Hard strings
- Context-heavy translation
- Cases where classic MT sounds too literal

---

## 6. SiliconFlow AI (Free Models)

### What it is

This backend uses SiliconFlow API with free models.

### Advantages

- Real free models are available
- Good low-cost entry into LLM-based translation
- Useful when you want more context-awareness than standard free MT

### Disadvantages and practical notes

- This is an LLM route, not a classic translation API
- Quality and speed depend on the active model
- Free model availability may change over time
- Always check the current model page for up-to-date pricing and limits

### Free tier

SiliconFlow states that new users receive:
- `14 RMB` free credit

It also offers free models.

### Paid pricing

- Free models: `0`
- Paid models: depends on the model

### Where to get it

- Dashboard: https://cloud.siliconflow.com/
- Billing rules: https://docs.siliconflow.com/en/faqs/billing-rules
- Rate limits: https://docs.siliconflow.com/en/userguide/rate-limits/rate-limit-and-upgradation
- Models page: https://cloud.siliconflow.com/models/text/chat

### How to get the API key

1. Register on SiliconFlow
2. Open `API Keys`
3. Create a key
4. Paste it into the `SiliconFlow API Key` field in the app

### Best use case

- Free or near-free LLM translation
- Better context handling than basic free MT

---

## 7. SiliconFlow AI (DeepSeek-V3.2)

### What it is

This backend uses `DeepSeek-V3.2` through SiliconFlow.

### Advantages

- Usually stronger than the free SiliconFlow models
- Better suited for difficult strings and context-heavy translation
- Good choice when you want a stronger LLM backend

### Disadvantages and practical notes

- Not a true free everyday backend
- Can be slower than classic MT services
- Current pricing should always be checked on SiliconFlow's model page

### Free tier

New SiliconFlow users receive:
- `14 RMB` free credit

However, this backend should generally be treated as a paid or limited-credit LLM option.

### Paid pricing

Check the current model pricing here:
- https://cloud.siliconflow.com/models/text/chat

### Where to get it

- Dashboard: https://cloud.siliconflow.com/
- Billing rules: https://docs.siliconflow.com/en/faqs/billing-rules
- Models page: https://cloud.siliconflow.com/models/text/chat

### How to use it

1. Get a normal SiliconFlow API key
2. Paste it into the `SiliconFlow API Key` field
3. Select `SiliconFlow AI (DeepSeek-V3.2)` in the backend list

### Best use case

- When free SiliconFlow models are not strong enough
- When you want stronger LLM-based translation quality

---

## 8. DeepL API Free

### What it is

This is the official DeepL API Free backend.

It is one of the most recommended backends for this app.

### Advantages

- Very strong translation quality
- Often sounds more natural on UI strings and standard text
- Official API
- Great balance of quality and free usage

### Disadvantages and practical notes

- The free monthly limit is capped
- Very large projects can hit the monthly threshold
- After Free usage, you need a paid DeepL API plan

### Free tier

DeepL API Free currently allows:
- `500,000 characters per month`

DeepL API Free keys are usually recognizable by the `:fx` suffix.

### Paid pricing

After Free usage, DeepL uses a paid API plan.

Always check the current official pricing page:
- https://www.deepl.com/pro-api

### Where to get it

- Create API account: https://www.deepl.com/en/pro/change-plan#developer
- API keys page: https://www.deepl.com/your-account/keys
- Authentication docs: https://developers.deepl.com/docs/getting-started/auth
- Usage limits: https://developers.deepl.com/docs/resources/usage-limits
- Product page: https://www.deepl.com/en/pro-api

### How to get the API key

1. Create a DeepL API account
2. Choose the free API plan
3. Open the `API Keys` tab
4. Copy your key
5. Paste it into the `DeepL API Key` field in the app

### Best use case

- Best official free translation quality
- Natural output
- UI and standard text translation

---

## Simple comparison

### Best quality

- `DeepL API Free`
- `Gemini Vertex AI`
- `SiliconFlow AI (DeepSeek-V3.2)`

### Fastest options

- `Google Cloud AI`
- `Google Translate Free (Web)`
- `DeepL API Free`

### Best free options

- `DeepL API Free`
- `Google Translate Free (Web)`
- `SiliconFlow AI (Free Models)`
- `LibreTranslate AI` local mode

### Best low-cost bulk option

- `Cloudflare Worker AI (M2M100)`

### Best local-only option

- `LibreTranslate AI`

## Final practical advice

- Start with `DeepL API Free` if you want the best official free quality
- Use `Google Cloud AI` if you want speed and reliability on large projects
- Use `Google Translate Free (Web)` if you want the easiest no-key workflow
- Use `Gemini` or `SiliconFlow DeepSeek` for difficult strings and context-heavy translation
- Use `Cloudflare Worker AI (M2M100)` when cost matters more than maximum naturalness
- Use `LibreTranslate AI` when you want local-only translation and do not need top-tier quality
