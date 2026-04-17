# SqliteClone Makefile

# Variables
JAVA_HOME := /usr/lib/jvm/java-21-openjdk-amd64
export JAVA_HOME

MVN = mvn
JAR = target/aerosql.jar

.PHONY: all build run test clean help

all: build

## build: Build the project and generate the JAR
build:
	$(MVN) -B package -DskipTests

## run: Run the application (Usage: make run ARGS="db.db .dbinfo")
run:
	@if [ ! -f $(JAR) ]; then $(MAKE) build; fi
	java --enable-preview --enable-native-access=ALL-UNNAMED -jar $(JAR) $(ARGS)

## dev: Run the application with techgrant.db for development
dev:
	$(MAKE) run ARGS="techgrant.db"

## test: Run unit tests
test:
	$(MVN) test

## clean: Remove build artifacts
clean:
	$(MVN) clean
	rm -rf target/

## help: Show this help message
help:
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@grep -E '^##' Makefile | sed -e 's/## //g' -e 's/: /	- /'
