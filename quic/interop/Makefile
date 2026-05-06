# Local iteration loop for the quic-interop-runner endpoint image.
#
#   make build              # compile JVM dist + build Docker image
#   make smoke              # quick handshake test against a local picoquic
#   make smoke-down         # tear down the smoke environment
#   make image-name         # print the image tag (for use in implementations_quic.json)
#
# These targets are deliberately thin wrappers — the real harness is the
# `quic-interop-runner` repo (clone separately and register `amethyst` with
# image=amethyst-quic-interop:latest, role=client).
IMAGE          ?= amethyst-quic-interop:latest
REPO_ROOT      := $(shell git rev-parse --show-toplevel)
DIST_DIR       := build/install/quic-interop
SMOKE_NET      := amethyst-quic-interop-smoke-net
SMOKE_PICOQUIC := amethyst-quic-interop-smoke-picoquic
SMOKE_CLIENT   := amethyst-quic-interop-smoke-client

.PHONY: build dist docker smoke smoke-down image-name clean

build: docker

dist:
	cd $(REPO_ROOT) && ./gradlew :quic-interop:installDist

docker: dist
	cd $(REPO_ROOT) && docker build -t $(IMAGE) -f quic/interop/Dockerfile .

# Stand up picoquic + our endpoint on a private Docker bridge network. This
# avoids `--network host`, which on Docker Desktop for Mac is misleadingly
# *not* the Mac host's network — it's the LinuxKit VM's, and port forwarding
# trickery makes 127.0.0.1 unreliable.
#
# We pass the resolved IP directly rather than relying on Docker's embedded
# DNS — the base image (martenseemann/quic-network-simulator-endpoint) ships
# a /etc/resolv.conf primed for the runner's sim that breaks bridge-network
# name resolution from the JVM.
smoke: docker smoke-down
	docker network create $(SMOKE_NET) >/dev/null 2>&1 || true
	docker run -d --name $(SMOKE_PICOQUIC) \
	    --network $(SMOKE_NET) \
	    privateoctopus/picoquic:latest picoquicdemo -p 4433 >/dev/null
	@PICOQUIC_IP=$$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $(SMOKE_PICOQUIC)); \
	    echo "picoquic at $$PICOQUIC_IP:4433 (in $(SMOKE_NET))"; \
	    docker run --rm --name $(SMOKE_CLIENT) \
	        --network $(SMOKE_NET) \
	        -e SMOKE_MODE=1 \
	        -e ROLE=client \
	        -e TESTCASE=handshake \
	        -e REQUESTS="https://$$PICOQUIC_IP:4433/" \
	        $(IMAGE)
	@$(MAKE) smoke-down

smoke-down:
	@docker rm -f $(SMOKE_PICOQUIC) $(SMOKE_CLIENT) >/dev/null 2>&1 || true
	@docker network rm $(SMOKE_NET) >/dev/null 2>&1 || true

image-name:
	@echo $(IMAGE)

clean:
	cd $(REPO_ROOT) && ./gradlew :quic-interop:clean
	docker image rm -f $(IMAGE) 2>/dev/null || true
