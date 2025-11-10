# COLA - Coding Agent Platform

<div align="center">

![COLA Logo](https://via.placeholder.com/150x150?text=COLA)

**An AI-powered coding agent platform for intelligent code planning, generation, compilation, testing, and deployment**

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M3-blue.svg)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

## 🚀 Overview

COLA (Coding Agent Platform) is a production-ready, AI-powered platform that assists developers by understanding natural language requirements, planning software architecture, generating production-quality code, and handling compilation, testing, and deployment - all through an intuitive web interface.

### Key Features

- 🤖 **AI-Powered Code Generation** - Leverages Claude 3.5 Sonnet and GPT-4 for intelligent code generation
- 📋 **Intelligent Planning** - Creates detailed implementation plans and architecture diagrams
- 🔨 **Automated Compilation** - Supports Maven, Gradle, npm/yarn in isolated Docker containers
- 🧪 **Automated Testing** - Executes unit and integration tests with coverage reports
- 🚀 **One-Click Deployment** - Generates Dockerfiles, K8s manifests, and deploys applications
- 💬 **Real-time Chat Interface** - Interactive AI assistant via WebSocket
- 🎨 **Modern Web UI** - Professional dark-themed interface with Monaco editor integration
- 🔐 **Enterprise Security** - JWT authentication, RBAC, encrypted data, sandboxed execution
- 📊 **Monitoring & Analytics** - Prometheus metrics, Grafana dashboards, audit logging

## 📋 Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Architecture](#architecture)
- [API Documentation](#api-documentation)
- [Development](#development)
- [Deployment](#deployment)
- [Contributing](#contributing)
- [License](#license)

## 🔧 Prerequisites

### Required

- **Java 17** or higher
- **Maven 3.6+** or Gradle 8+
- **PostgreSQL 16+**
- **Docker** (for containerized builds and deployments)
- **Anthropic API Key** (for Claude AI)

### Optional

- **OpenAI API Key** (for GPT-4 fallback)
- **Kubernetes** (for production deployments)
- **Git** (for version control)

## ⚡ Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/cola-agent-platform.git
cd cola-agent-platform
```

### 2. Set Up Environment Variables

```bash
cp .env.example .env
# Edit .env and add your API keys
nano .env
```

**Required Configuration:**

```env
ANTHROPIC_API_KEY=sk-ant-your-api-key-here
JWT_SECRET=your-super-secret-jwt-key-min-256-bits
```

### 3. Start with Docker Compose (Recommended)

```bash
docker-compose up -d
```

This will start:
- PostgreSQL database
- COLA Agent application
- (Optional) Prometheus & Grafana for monitoring

### 4. Access the Application

Open your browser and navigate to:

```
http://localhost:8080
```

**Default Credentials:**
- Username: `admin`
- Password: `admin123` (⚠️ Change immediately in production)

### 5. Start Using COLA

1. Create a new project
2. Describe what you want to build
3. Let COLA plan and generate code
4. Review, compile, test, and deploy!

## ⚙️ Configuration

### Application Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-3-5-sonnet-20241022
          temperature: 0.7
          max-tokens: 4096

cola:
  agent:
    primary-provider: anthropic
    streaming-enabled: true
    max-context-tokens: 128000
```

### Database Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cola_db
    username: cola_user
    password: cola_password
```

### Docker Configuration

For custom Docker setup, modify `docker-compose.yml`:

```yaml
services:
  cola-agent:
    environment:
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
      DB_HOST: postgres
      SERVER_PORT: 8080
```

## 🏗️ Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Frontend Layer                         │
│         (HTML5, CSS3, JavaScript + WebSocket)           │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                  Spring Boot Backend                     │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │   Agent      │  │  Compilation │  │  Deployment  │ │
│  │   Service    │  │   Service    │  │   Service    │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
│                                                          │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│              Integration Layer                           │
│  Spring AI + Claude/GPT | Docker | PostgreSQL           │
└─────────────────────────────────────────────────────────┘
```

### Technology Stack

**Backend:**
- Spring Boot 3.2.0
- Spring AI 1.0.0-M3
- Spring Security + JWT
- Spring WebSocket
- Spring Data JPA
- Flyway Migration

**Frontend:**
- HTML5 / CSS3
- Vanilla JavaScript (ES6+)
- SockJS + STOMP
- Font Awesome Icons

**Database:**
- PostgreSQL 16
- Flyway for migrations

**Infrastructure:**
- Docker
- Docker Compose
- Kubernetes (optional)

**AI Models:**
- Anthropic Claude 3.5 Sonnet (Primary)
- OpenAI GPT-4 Turbo (Fallback)

## 📚 API Documentation

### REST API Endpoints

Once the application is running, access the interactive API documentation:

**Swagger UI:** `http://localhost:8080/swagger-ui.html`

**OpenAPI JSON:** `http://localhost:8080/api-docs`

### Key Endpoints

#### Projects

```http
POST   /api/v1/projects              # Create project
GET    /api/v1/projects              # List projects
GET    /api/v1/projects/{id}         # Get project
PUT    /api/v1/projects/{id}         # Update project
DELETE /api/v1/projects/{id}         # Delete project
```

#### Agent

```http
POST   /api/v1/agent/chat            # Chat with agent
POST   /api/v1/agent/plan            # Generate plan
POST   /api/v1/agent/generate        # Generate code (streaming)
POST   /api/v1/agent/fix             # Fix errors
```

#### WebSocket

```
/ws                                   # WebSocket endpoint
/app/chat                            # Send chat message
/topic/messages                      # Subscribe to messages
/user/{userId}/queue/code            # Subscribe to code generation
```

## 🛠️ Development

### Building from Source

```bash
# Clean and build
mvn clean package

# Skip tests
mvn clean package -DskipTests

# Run application
mvn spring-boot:run
```

### Running Tests

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Database Migrations

```bash
# Run migrations
mvn flyway:migrate

# Rollback
mvn flyway:undo

# Clean database (⚠️ Destructive)
mvn flyway:clean
```

### Development Mode

```bash
# Run with dev profile
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# Enable debug logging
mvn spring-boot:run -Dspring-boot.run.arguments="--logging.level.com.cola.agent=DEBUG"
```

## 🚀 Deployment

### Docker Deployment

```bash
# Build image
docker build -t cola-agent:latest .

# Run container
docker run -d \
  -p 8080:8080 \
  -e ANTHROPIC_API_KEY=your-key \
  -e DB_HOST=postgres \
  --name cola-agent \
  cola-agent:latest
```

### Kubernetes Deployment

```bash
# Create namespace
kubectl create namespace cola

# Create secrets
kubectl create secret generic cola-secrets \
  --from-literal=anthropic-api-key=your-key \
  --from-literal=jwt-secret=your-jwt-secret \
  -n cola

# Apply manifests (to be created)
kubectl apply -f k8s/ -n cola

# Check deployment
kubectl get pods -n cola
```

### Production Checklist

- [ ] Change default admin password
- [ ] Set strong JWT secret (min 256 bits)
- [ ] Configure database with strong password
- [ ] Enable HTTPS/TLS
- [ ] Set up backup strategy
- [ ] Configure monitoring and alerts
- [ ] Review and adjust resource limits
- [ ] Enable audit logging
- [ ] Configure CORS properly
- [ ] Set up rate limiting

## 📊 Monitoring

### Prometheus Metrics

Metrics are exposed at: `http://localhost:8080/actuator/prometheus`

Key metrics:
- `cola_agent_requests_total` - Total API requests
- `cola_code_generation_duration_seconds` - Code generation time
- `cola_compilation_success_rate` - Compilation success rate
- `jvm_memory_used_bytes` - JVM memory usage

### Grafana Dashboards

Start monitoring stack:

```bash
docker-compose --profile monitoring up -d
```

Access Grafana: `http://localhost:3000`

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Development Workflow

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Spring AI](https://spring.io/projects/spring-ai) - AI integration framework
- [Anthropic Claude](https://www.anthropic.com/) - Primary AI model
- [OpenAI](https://openai.com/) - Fallback AI model
- [Monaco Editor](https://microsoft.github.io/monaco-editor/) - Code editor component

## 📞 Support

- **Documentation:** [docs.cola-agent.com](https://docs.cola-agent.com)
- **Issues:** [GitHub Issues](https://github.com/yourusername/cola-agent-platform/issues)
- **Discussions:** [GitHub Discussions](https://github.com/yourusername/cola-agent-platform/discussions)
- **Email:** support@cola-agent.com

---

<div align="center">

**Built with ❤️ by the COLA Team**

[Website](https://cola-agent.com) • [Documentation](https://docs.cola-agent.com) • [Blog](https://blog.cola-agent.com)

</div>
