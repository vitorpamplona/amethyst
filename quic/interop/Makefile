# Local iteration loop for the quic-interop-runner endpoint image.
#
#   make build              # compile JVM dist + build Docker image
#   make image-name         # print the image tag (for use in implementations_quic.json)
#   make clean              # nuke the dist + image
#
# Real testing happens via the runner: clone quic-interop-runner separately
# and drive it with `quic/interop/run-matrix.sh -s <peer> -t <tests>`.
IMAGE     ?= amethyst-quic-interop:latest
REPO_ROOT := $(shell git rev-parse --show-toplevel)
DIST_DIR  := build/install/quic-interop

.PHONY: build dist docker image-name clean

build: docker

dist:
	cd $(REPO_ROOT) && ./gradlew :quic-interop:installDist

docker: dist
	cd $(REPO_ROOT) && docker build -t $(IMAGE) -f quic/interop/Dockerfile .

image-name:
	@echo $(IMAGE)

clean:
	cd $(REPO_ROOT) && ./gradlew :quic-interop:clean
	docker image rm -f $(IMAGE) 2>/dev/null || true
