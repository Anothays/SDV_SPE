.PHONY: help setup down logs load-test stress-test telemetry-test

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

setup: ## Démarre toute la stack (sso, role-manager, db, kafka, monitoring)
	docker compose up -d --build

down: ## Arrête et supprime les containers
	docker compose down

logs: ## Suit les logs de role-manager et sso
	docker compose logs -f role-manager sso

load-test: ## Lance le test de charge JMeter (50 users / 60s)
	docker compose --profile testing run --rm jmeter

stress-test: ## Lance le test de stress JMeter (10 -> 200 users)
	TEST_PLAN=profil-api-stress-test.jmx docker compose --profile testing run --rm jmeter

telemetry-test: ## Lance le stress test de télémétrie (100 users -> Kafka)
	TEST_PLAN=telemetry-stress-test.jmx docker compose --profile testing run --rm jmeter
