# Local iteration loop for the quic-interop-runner endpoint image.
#
#   make build              # compile JVM dist + build Docker image
#   make smoke              # quick handshake test against a local picoquic
#   make image-name         # print the image tag (for use in implementations_quic.json)
#
# These targets are deliberately thin wrappers — the real harness is the
# `quic-interop-runner` repo (clone separately and register `amethyst` with
# image=amethyst-quic-interop:latest, role=client).
IMAGE          ?= amethyst-quic-interop:latest
REPO_ROOT      := $(shell git rev-parse --show-toplevel)
DIST_DIR       := build/install/quic-interop
PICOQUIC_PORT  ?= 4433

.PHONY: build dist docker smoke image-name clean

build: docker

dist:
	cd $(REPO_ROOT) && ./gradlew :quic-interop:installDist

docker: dist
	cd $(REPO_ROOT) && docker build -t $(IMAGE) -f quic/interop/Dockerfile .

smoke: docker
	@docker rm -f amethyst-quic-interop-smoke >/dev/null 2>&1 || true
	docker run --rm --name amethyst-quic-interop-smoke \
	    --network host \
	    -e ROLE=client \
	    -e TESTCASE=handshake \
	    -e REQUESTS="https://127.0.0.1:$(PICOQUIC_PORT)/" \
	    $(IMAGE)

image-name:
	@echo $(IMAGE)

clean:
	cd $(REPO_ROOT) && ./gradlew :quic-interop:clean
	docker image rm -f $(IMAGE) 2>/dev/null || true
