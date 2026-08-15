SHELL := $(shell command -v bash)
.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:
.DEFAULT_GOAL := run

JAR           := target/aster-0.0.1.jar
NATIVE_BIN    := aster
NATIVE_CONFIG := src/main/resources/META-INF/native-image
AGENT_OPTS    := -agentlib:native-image-agent=config-merge-dir=$(NATIVE_CONFIG)
DEBUG_OPTS    := -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005

.PHONY: package run debug trace native move-native run-native clean

package:
	mvn clean package

run: package
	java -jar $(JAR)

debug: package
	java "$(DEBUG_OPTS)" -jar $(JAR)

trace: package
	mkdir -p $(NATIVE_CONFIG)
	rm -f $(NATIVE_CONFIG)/.lock
	java $(AGENT_OPTS) -jar $(JAR)

native:
	mvn -Pnative native:compile

move-native:
	if [[ -f ./target/$(NATIVE_BIN) ]]; then mv ./target/$(NATIVE_BIN) .; fi

run-native: move-native
	./$(NATIVE_BIN)

clean:
	mvn clean
	rm -f ./$(NATIVE_BIN)