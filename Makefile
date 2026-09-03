.PHONY: help build test unit-test integration-test verify lint fmt clean package release

# This repository intentionally uses the system Maven installation (3.9.0+).
MVN ?= mvn
COMPOSE ?= docker compose -f docker-compose.test.yml
# No default: an immutable, digest-pinned image is required, e.g.
#   STREAMLINE_IMAGE=ghcr.io/streamlinelabs/streamline@sha256:<64 hex chars> make integration-test
# ":latest" and other mutable/absent defaults are hard-blocked (see
# scripts/require-image-digest.sh) rather than silently substituted.
export STREAMLINE_IMAGE ?=

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

release: ## Verify a validated tag and hard-block until byte-identical Central publication exists
	@test -n "$(RELEASE_TAG)" || { echo "RELEASE_TAG is required" >&2; exit 1; }
	scripts/validate-release-tag.sh "$(RELEASE_TAG)"
	$(MVN) verify -P release
	scripts/verify-release-artifacts.sh
	scripts/central-publish-gate.sh

integration-test: ## Run integration tests against a live server (requires Docker + an explicit digest-pinned STREAMLINE_IMAGE)
	scripts/require-image-digest.sh "$(STREAMLINE_IMAGE)"
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
