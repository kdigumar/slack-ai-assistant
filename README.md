# Enterprise Slack AI Support Assistant

A scalable, multi-product Slack AI assistant built with Spring Boot, OpenAI, Redis, Kafka, and Kubernetes.

## Architecture

```
┌──────────────────┐     ┌─────────────────┐     ┌──────────────────┐
│   Slack Events   │────▶│  Spring Boot    │────▶│   OpenAI GPT-4o  │
│   (#artemishelp  │     │  Application    │     │  (Intent + RAG)  │
│    #b360help     │     │                 │     │                  │
│    #velocityhelp)│     └────────┬────────┘     └──────────────────┘
└──────────────────┘              │
                                  ▼
                    ┌─────────────────────────┐
                    │     Product Router      │
                    │  (Config-driven routing)│
                    └─────────────────────────┘
                                  │
          ┌───────────────────────┼───────────────────────┐
          ▼                       ▼                       ▼
   ┌──────────────┐       ┌──────────────┐       ┌──────────────┐
   │   Artemis    │       │     B360     │       │   Velocity   │
   │  (User Mgmt) │       │  (Analytics) │       │   (CI/CD)    │
   └──────────────┘       └──────────────┘       └──────────────┘
```

## Features

- **Multi-Product Support**: Single bot serving Artemis, B360, and Velocity from one codebase
- **Config-Driven Routing**: Add new products via YAML configuration
- **Distributed Cache**: Redis for horizontal scaling
- **Message Queue**: Kafka for durability and scaling
- **Circuit Breakers**: Resilience4j for fault tolerance
- **Kubernetes Ready**: HPA, ConfigMaps, Secrets, Ingress

## Quick Start

### Prerequisites
- Java 21
- Maven 3.9+
- Slack App with Bot Token and Signing Secret
- OpenAI API Key

### Local Development

```bash
# Set environment variables
export SLACK_BOT_TOKEN="xoxb-your-token"
export SLACK_SIGNING_SECRET="your-signing-secret"
export OPENAI_API_KEY="sk-your-api-key"

# Run the application
mvn spring-boot:run
```

### With ngrok (for Slack Events)

```bash
# In terminal 1
mvn spring-boot:run

# In terminal 2
ngrok http 8080

# Add ngrok URL to Slack App Event Subscriptions:
# https://your-ngrok-url.ngrok-free.dev/slack/events
```

## Configuration

### Adding a New Product

1. Add to `application.yml`:
```yaml
products:
  definitions:
    your-product:
      channels:
        - your-product-help
      intent-mapping-file: intent-mappings/your-product.json
      rag-docs-file: rag-docs/your-product.json
      api-base-url: https://your-product-api.internal
```

2. Create `intent-mappings/your-product.json`
3. Create `rag-docs/your-product.json`
4. Add API handlers in `ProductApiService.java`

## Project Structure

```
src/main/java/com/enterprise/slackassistant/
├── config/           # Configuration classes
├── controller/       # Slack servlet registration
├── dto/              # Data transfer objects
├── exception/        # Custom exceptions
├── kafka/            # Kafka producer/consumer
├── model/            # Domain models
└── service/          # Business logic
    ├── ProductRouterService.java    # Multi-product routing
    ├── IntentDetectionService.java  # OpenAI intent detection
    ├── ProductApiService.java       # API calls per product
    ├── RagService.java              # RAG document retrieval
    ├── SynthesisService.java        # Response synthesis
    └── CacheService.java            # Redis/in-memory cache
```

## Kubernetes Deployment

```bash
# Build Docker image
docker build -t slack-assistant:1.0.0 .

# Deploy to Kubernetes
kubectl apply -k k8s/
```

## Tech Stack

- **Framework**: Spring Boot 3.2
- **AI/LLM**: Spring AI + OpenAI GPT-4o
- **Slack**: Slack Bolt SDK
- **Cache**: Redis
- **Queue**: Kafka
- **Resilience**: Resilience4j
- **Container**: Docker + Kubernetes

## License

MIT

