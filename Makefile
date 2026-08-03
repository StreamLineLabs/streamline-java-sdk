.PHONY: help build test unit-test integration-test verify lint fmt clean package release

MVN ?= $(shell [ -x ./mvnw ] && echo ./mvnw || echo mvn)
COMPOSE ?= docker compose -f docker-compose.test.yml
# Override to test a specific build, e.g. STREAMLINE_IMAGE=ghcr.io/streamlinelabs/streamline:0.3.0
export STREAMLINE_IMAGE ?= ghcr.io/streamlinelabs/streamline:latest

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-16s\033[0m %s\n", $$1, $$2}'

build: ## Compile the SDK
	$(MVN) compile -q

test: unit-test ## Alias for unit-test

unit-test: ## Run unit tests only (no server required)
	$(MVN) test

verify: ## Build, unit-test, package and run static analysis (no server required)
	$(MVN) verify

lint: ## Run static analysis (SpotBugs)
	$(MVN) compile spotbugs:check

fmt: ## Format code (check only)
	@echo "Use IDE formatting or google-java-format"

clean: ## Clean build artifacts
	$(MVN) clean -q
	-$(COMPOSE) down -v >/dev/null 2>&1

package: ## Build JAR package
	$(MVN) package -q -DskipTests

release: ## Deploy to Maven Central
	$(MVN) deploy -P release -DskipTests

integration-test: ## Run integration tests against a live server (requires Docker)
	$(COMPOSE) up -d
	@echo "Waiting for Streamline ($(STREAMLINE_IMAGE))..."
	@for i in $$(seq 1 30); do \
		if curl -sf http://localhost:9094/health/live > /dev/null 2>&1; then \
			echo "Server ready"; \
			break; \
		fi; \
		sleep 2; \
	done
	STREAMLINE_INTEGRATION=1 $(MVN) verify -Pintegration; \
		status=$$?; \
		$(COMPOSE) down -v; \
		exit $$status
