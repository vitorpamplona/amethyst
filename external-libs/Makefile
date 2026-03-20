FLAVOUR = monero

all: arm64-v8a armeabi-v7a x86_64 x86 include/wallet2_api.h

arm64-v8a: $(FLAVOUR) android64.Dockerfile
	-rm -rf arm64-v8a
	-docker container rm $(FLAVOUR)-android-arm64 -f
	docker build --network=host -f android64.Dockerfile -t $(FLAVOUR)-android-arm64 $(FLAVOUR)
	docker create --network=host -it --name $(FLAVOUR)-android-arm64 $(FLAVOUR)-android-arm64 bash
	docker cp $(FLAVOUR)-android-arm64:/opt/android/prefix/lib/. arm64-v8a/
	docker cp $(FLAVOUR)-android-arm64:/src/build/release/lib/. arm64-v8a/$(FLAVOUR)

armeabi-v7a: $(FLAVOUR) android32.Dockerfile
	-rm -rf armeabi-v7a
	-docker container rm $(FLAVOUR)-android-arm32 -f
	docker build --network=host -f android32.Dockerfile -t $(FLAVOUR)-android-arm32 $(FLAVOUR)
	docker create --network=host -it --name $(FLAVOUR)-android-arm32 $(FLAVOUR)-android-arm32 bash
	docker cp $(FLAVOUR)-android-arm32:/opt/android/prefix/lib/. armeabi-v7a
	docker cp $(FLAVOUR)-android-arm32:/src/build/release/lib/. armeabi-v7a/$(FLAVOUR)

x86_64: $(FLAVOUR) android64_x86.Dockerfile
	-rm -rf x86_64
	-docker container rm $(FLAVOUR)-android-x86_64 -f
	docker build --network=host -f android64_x86.Dockerfile -t $(FLAVOUR)-android-x86_64 $(FLAVOUR)
	docker create --network=host -it --name $(FLAVOUR)-android-x86_64 $(FLAVOUR)-android-x86_64 bash
	docker cp $(FLAVOUR)-android-x86_64:/opt/android/prefix/lib/. x86_64
	docker cp $(FLAVOUR)-android-x86_64:/src/build/release/lib/. x86_64/$(FLAVOUR)

x86: $(FLAVOUR) android32_x86.Dockerfile
	-rm -rf x86
	-docker container rm $(FLAVOUR)-android-x86 -f
	docker build --network=host -f android32_x86.Dockerfile -t $(FLAVOUR)-android-x86 $(FLAVOUR)
	docker create --network=host -it --name $(FLAVOUR)-android-x86 $(FLAVOUR)-android-x86 bash
	docker cp $(FLAVOUR)-android-x86:/opt/android/prefix/lib/. x86
	docker cp $(FLAVOUR)-android-x86:/src/build/release/lib/. x86/$(FLAVOUR)

include/wallet2_api.h: $(FLAVOUR) include $(FLAVOUR)/src/wallet/api/wallet2_api.h
	cp $(FLAVOUR)/src/wallet/api/wallet2_api.h include/wallet2_api.h

include:
	mkdir include

clean:
	-rm -rf arm64-v8a
	-rm -rf armeabi-v7a
	-rm -rf x86_64
	-rm -rf x86
	-rm -rf include

distclean: clean
	-docker container rm $(FLAVOUR)-android-arm64 -f
	-docker container rm $(FLAVOUR)-android-arm32 -f
	-docker container rm $(FLAVOUR)-android-x86_64 -f
	-docker container rm $(FLAVOUR)-android-x86 -f
	-docker image rm $(FLAVOUR)-android-arm64 -f
	-docker image rm $(FLAVOUR)-android-arm32 -f
	-docker image rm $(FLAVOUR)-android-x86_64 -f
	-docker image rm $(FLAVOUR)-android-x86 -f
	-rm $(FLAVOUR)

$(FLAVOUR):
	$(error Please ln -s $(FLAVOUR))

.PHONY: all clean distclean
