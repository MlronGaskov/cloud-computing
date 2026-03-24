COMPOSE = docker compose -f ./docker/docker-compose.test.yml

.PHONY: cluster-up cluster-down test test-local logs clean

cluster-up:
	$(COMPOSE) up -d coordinator worker1 worker2

cluster-down:
	$(COMPOSE) down --remove-orphans

test-local:
	clojure -M -m cloud.test-runner

test:
	$(COMPOSE) up -d coordinator worker1 worker2
	$(COMPOSE) run --rm tests
	$(COMPOSE) down --remove-orphans

integration-test:
	$(COMPOSE) up -d coordinator worker1 worker2
	$(COMPOSE) run --rm tests sh -lc 'clojure -M -m integration.test-runner'
	$(COMPOSE) down --remove-orphans

logs:
	$(COMPOSE) logs -f coordinator worker1 worker2

clean:
	$(COMPOSE) down -v --remove-orphans
