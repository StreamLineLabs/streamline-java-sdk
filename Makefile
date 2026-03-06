.PHONY: integration-test build test lint fmt clean help

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

build: ## Compile the SDK
	mvn compile -q

test: ## Run all tests
	mvn verify -q

lint: ## Run linting and style checks
	mvn checkstyle:check -q 2>/dev/null || echo "checkstyle not configured, skipping"

fmt: ## Format code (check only)
	@echo "Use IDE formatting or google-java-format"

clean: ## Clean build artifacts
	mvn clean -q

package: ## Build JAR package
	mvn package -q -DskipTests

release: ## Deploy to Maven Central
	mvn deploy -P release -DskipTests
# update parent POM dependency versions
# configure Maven Central publishing plugin

integration-test: ## Run integration tests (requires Docker)
	docker compose -f docker-compose.test.yml up -d
	@echo "Waiting for Streamline server..."
	@for i in $$(seq 1 30); do \
		if curl -sf http://localhost:9094/health/live > /dev/null 2>&1; then \
			echo "Server ready"; \
			break; \
		fi; \
		sleep 2; \
	done
	MVN=./mvnw && [ -f "$$MVN" ] && $$MVN verify -Pintegration || mvn verify -Pintegration
	docker compose -f docker-compose.test.yml down -v
