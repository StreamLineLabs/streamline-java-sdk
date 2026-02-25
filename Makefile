.PHONY: build test lint fmt clean help

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
