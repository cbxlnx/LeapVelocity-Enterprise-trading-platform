# LeapVelocity Project

LeapVelocity is an enterprise trading platform built as a layered set of services. The platform is designed to support secure order placement, validation, execution, and portfolio tracking across a distributed architecture. It combines REST APIs, asynchronous event processing, and containerized infrastructure so that trading workflows can be developed, tested, and deployed consistently.

## Project Definition

The platform is structured around multiple collaborating services:

- An Angular front end provides the user-facing trading experience.
- A Trade REST API, built with Java and Spring Boot, contains core trading logic such as order placement, order cancellation, validation against business and risk rules, and order history.
- A Portfolio REST API, also built with Java and Spring Boot, is responsible for portfolio views, positions, and balances.
- Both backend services persist data to PostgreSQL using JPA.
- A NestJS authentication service issues and validates JWTs to secure API access, following an OAuth2-style login flow that is MFA-ready and supports role-based access control.
- Trade events are published to Kafka for asynchronous downstream processing.
- A Trade Executor consumes Kafka trade events and interacts asynchronously with the rest of the platform to simulate or process execution workflows.

The current repository includes the infrastructure and core services needed to run the trading platform locally, including the Spring Boot trade API, authentication stub, PostgreSQL database, Kafka broker, Kafka UI, and execution engine.

Authentication setup, service boundaries, request examples, and verification details are documented here:  
[Authentication sprint report](docs/authentication-sprint.md)

## Architecture Overview

The local Docker-based environment currently defines these runtime services:

- `auth-stub`: authentication service running on port `4000`
- `trade-api`: Spring Boot trading API running on port `8081`
- `execution-engine`: asynchronous execution processor consuming Kafka events
- `trade-db`: PostgreSQL database running on port `5432`
- `kafka`: Kafka broker running on port `9092`
- `kafka-ui`: web UI for inspecting Kafka topics and brokers, exposed on port `8090`

## Build & Deploy

### Prerequisites

Make sure the following are installed on your machine:

- Docker
- Docker Compose
- Git

### Environment Configuration

Create a `.env` file in the project root if one does not already exist. At minimum, set the following values:

```env
DB_NAME=leapvelocity_trading_db
DB_USER=postgres
DB_PASSWORD=change_me
POSTGRES_PORT=5432
APP_PORT=8081
AUTH_STUB_PORT=4000
KAFKA_PORT=9092
KAFKA_UI_PORT=8090
JWT_SECRET=your-secure-secret
JWT_ISSUER=leapvelocity-auth
JWT_EXPIRES_IN=1h
REFRESH_TOKEN_EXPIRES_IN=7d
```

### Important Notes

- JWT_SECRET is required. The authentication and API services will not start without it.
- DB_PASSWORD must be set for PostgreSQL to initialize correctly.

### Local Build and Startup

From the project root, build and start the full local platform with:

```
docker-compose up -d --build
```

To verify that the services are running:
```
docker-compose ps
```

To stop the environment
```
docker-compose down
```

To stop the environment and remove the database volume:
```
docker-compose down -v
```

### Database Reset Note

Use the volume-removal command only when you want a clean database reset.

### Deployment Notes

The provided docker-compose.yml is intended for local development and integration testing. It orchestrates the application services, database, Kafka broker, and Kafka UI in a single local environment.

For production deployment, the same service boundaries can be deployed into a managed container platform with environment-specific secrets, networking, monitoring, and scaling policies.

### Local Service Access

- http://localhost:8081
- http://localhost:4000
- localhost:9092
- http://localhost:8090

Kafka UI is connected to the local Kafka broker defined in Docker Compose and can be used to inspect brokers, topics, and consumer groups.

## Branching Strategy

We follow a GitFlow-style branching model.

- main contains production-ready code.
- develop is the primary integration branch for ongoing work.
- Feature branches are created from develop and merged back into develop through pull requests.
- Hotfix branches are created from main for urgent production fixes and are merged back into both main and develop.

All pull requests are reviewed and approved before they are merged.

## Team

- Oleksandra Tiankina
- Anna Radcenko
- Daniel Fegan
- Kateryna Kozelko

## Links to Shared Resources

[Leap Velocity Team Jira Board](https://leapvelocity.atlassian.net?continue=https%3A%2F%2Fleapvelocity.atlassian.net%2Fwelcome%2Fsoftware&atlOrigin=eyJpIjoiN2M1OTM0NjMwZDliNGE3NDg1M2ZkYWNlOWU4NTY2MWUiLCJwIjoiaiJ9)

[Miro Board](https://miro.com/welcome/SUc4TFd6cVQzNFRnNjNMaStGOWNUUW5UYmE0TzFaS2EreW0wM3dNMWZMc2ltblFhTFU0MWlrcW9lZjVZc0dRQXJQa0M1ZVlKMDVPUjVUTjczTmhjeS9NdHk1YWdxNmNEN0NGTHlBcXFOaE4zK1ptQVRkM1FUZmFTU0ZaYVJPSTRMRE5hYVVlWmxLUXAzWm9PYmNqcmpBPT0hdjE=?share_link_id=724461592640)



