SHELL := $(shell command -v bash)
.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:


run:
	mvn clean package && java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image   -jar target/aster-0.0.1.jar

native:
	mvn -Pnative native:compile

move-native:
	if [[ -f ./target/aster ]]; then mv target/aster .; fi

run-native: move-native
	./aster