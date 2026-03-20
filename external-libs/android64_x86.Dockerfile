FROM debian:stable

RUN set -x && apt-get update && apt-get install -y unzip automake build-essential curl file pkg-config git python3 python-is-python3 libtool libtinfo5

WORKDIR /opt/android
## INSTALL ANDROID SDK
ENV ANDROID_SDK_REVISION 4333796
ENV ANDROID_SDK_HASH 92ffee5a1d98d856634e8b71132e8a95d96c83a63fde1099be3d86df3106def9
RUN set -x \
    && curl -O https://dl.google.com/android/repository/sdk-tools-linux-${ANDROID_SDK_REVISION}.zip \
    && echo "${ANDROID_SDK_HASH}  sdk-tools-linux-${ANDROID_SDK_REVISION}.zip" | sha256sum -c \
    && unzip sdk-tools-linux-${ANDROID_SDK_REVISION}.zip \
    && rm -f sdk-tools-linux-${ANDROID_SDK_REVISION}.zip

## INSTALL ANDROID NDK
ENV ANDROID_NDK_REVISION 17c
ENV ANDROID_NDK_HASH 3f541adbd0330a9205ba12697f6d04ec90752c53d6b622101a2a8a856e816589
RUN set -x \
    && curl -O https://dl.google.com/android/repository/android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip \
    && echo "${ANDROID_NDK_HASH}  android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip" | sha256sum -c \
    && unzip android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip \
    && rm -f android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip

ENV WORKDIR /opt/android
ENV ANDROID_SDK_ROOT ${WORKDIR}/tools
ENV ANDROID_NDK_ROOT ${WORKDIR}/android-ndk-r${ANDROID_NDK_REVISION}
ENV PREFIX /opt/android/prefix

ENV TOOLCHAIN_DIR ${WORKDIR}/toolchain
RUN set -x \
    && ${ANDROID_NDK_ROOT}/build/tools/make_standalone_toolchain.py \
         --arch x86_64 \
         --api 21 \
         --install-dir ${TOOLCHAIN_DIR} \
         --stl=libc++

#INSTALL cmake
ARG CMAKE_VERSION=3.14.6
ARG CMAKE_HASH=82e08e50ba921035efa82b859c74c5fbe27d3e49a4003020e3c77618a4e912cd
RUN set -x \
    && cd /usr \
    && curl -L -O https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz \
    && echo "${CMAKE_HASH}  cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz" | sha256sum -c \
    && tar -xzf /usr/cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz \
    && rm -f /usr/cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz
ENV PATH /usr/cmake-${CMAKE_VERSION}-Linux-x86_64/bin:$PATH

## Boost
ARG BOOST_VERSION=1_70_0
ARG BOOST_VERSION_DOT=1.70.0
ARG BOOST_HASH=430ae8354789de4fd19ee52f3b1f739e1fba576f0aded0897c3c2bc00fb38778
RUN set -x \
    && curl -L -o  boost_${BOOST_VERSION}.tar.bz2 https://boostorg.jfrog.io/artifactory/main/release/${BOOST_VERSION_DOT}/source/boost_${BOOST_VERSION}.tar.bz2 \
    && echo "${BOOST_HASH}  boost_${BOOST_VERSION}.tar.bz2" | sha256sum -c \
    && tar -xvf boost_${BOOST_VERSION}.tar.bz2 \
    && rm -f boost_${BOOST_VERSION}.tar.bz2 \
    && cd boost_${BOOST_VERSION} \
    && ./bootstrap.sh --prefix=${PREFIX}

ENV HOST_PATH $PATH
ENV PATH $TOOLCHAIN_DIR/x86_64-linux-android/bin:$TOOLCHAIN_DIR/bin:$PATH

ARG NPROC=4

# Build iconv for lib boost locale
ENV ICONV_VERSION 1.16
ENV ICONV_HASH e6a1b1b589654277ee790cce3734f07876ac4ccfaecbee8afa0b649cf529cc04
RUN set -x \
    && curl -O http://ftp.gnu.org/pub/gnu/libiconv/libiconv-${ICONV_VERSION}.tar.gz \
    && echo "${ICONV_HASH}  libiconv-${ICONV_VERSION}.tar.gz" | sha256sum -c \
    && tar -xzf libiconv-${ICONV_VERSION}.tar.gz \
    && rm -f libiconv-${ICONV_VERSION}.tar.gz \
    && cd libiconv-${ICONV_VERSION} \
    && CC=clang CXX=clang++ ./configure --build=x86_64-linux-gnu --host=x86_64-linux-android --prefix=${PREFIX} --disable-rpath \
    && make -j${NPROC} && make install

## Build BOOST
RUN set -x \
    && cd boost_${BOOST_VERSION} \
    && ./b2 --build-type=minimal link=static runtime-link=static --with-chrono --with-date_time --with-filesystem --with-program_options --with-regex --with-serialization --with-system --with-thread --with-locale --build-dir=android --stagedir=android toolset=clang threading=multi threadapi=pthread target-os=android -sICONV_PATH=${PREFIX} install -j${NPROC}

# download, configure and make Zlib
ENV ZLIB_VERSION 1.3.1
ENV ZLIB_HASH 9a93b2b7dfdac77ceba5a558a580e74667dd6fede4585b91eefb60f03b72df23
RUN set -x \
    && curl -O https://zlib.net/zlib-${ZLIB_VERSION}.tar.gz \
    && echo "${ZLIB_HASH}  zlib-${ZLIB_VERSION}.tar.gz" | sha256sum -c \
    && tar -xzf zlib-${ZLIB_VERSION}.tar.gz \
    && rm zlib-${ZLIB_VERSION}.tar.gz \
    && mv zlib-${ZLIB_VERSION} zlib \
    && cd zlib && CC=clang CXX=clang++ ./configure --static \
    && make -j${NPROC}

# open ssl
ARG OPENSSL_VERSION=3.0.5
ARG OPENSSL_HASH=aa7d8d9bef71ad6525c55ba11e5f4397889ce49c2c9349dcea6d3e4f0b024a7a
# openssl explicitly demands to be built by a clang that has a "/prebuilt/" somewhere along its path, so use the prebuilt version, but make sure to specify the target android api
RUN set -x \
    && curl -O https://www.openssl.org/source/openssl-${OPENSSL_VERSION}.tar.gz \
    && echo "${OPENSSL_HASH}  openssl-${OPENSSL_VERSION}.tar.gz" | sha256sum -c \
    && tar -xzf openssl-${OPENSSL_VERSION}.tar.gz \
    && rm openssl-${OPENSSL_VERSION}.tar.gz \
    && cd openssl-${OPENSSL_VERSION} \
    && export PATH=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH \
    && ./Configure android-x86_64 \
           -D__ANDROID_API__=21 \
           -static \
           no-shared no-tests \
           --with-zlib-include=${WORKDIR}/zlib/include --with-zlib-lib=${WORKDIR}/zlib/lib \
           --prefix=${PREFIX} --openssldir=${PREFIX} \
    && make -j${NPROC} \
    && make install_sw

# ZMQ
ARG ZMQ_VERSION=v4.3.2
ARG ZMQ_HASH=a84ffa12b2eb3569ced199660bac5ad128bff1f0
RUN set -x \
    && git clone https://github.com/zeromq/libzmq.git -b ${ZMQ_VERSION} \
    && cd libzmq \
    && test `git rev-parse HEAD` = ${ZMQ_HASH} || exit 1 \
    && ./autogen.sh \
    && CC=clang CXX=clang++ ./configure --prefix=${PREFIX} --host=x86_64-linux-android --enable-static --disable-shared \
    && make -j${NPROC} \
    && make install

# Sodium
ARG SODIUM_VERSION=1.0.18
ARG SODIUM_HASH=4f5e89fa84ce1d178a6765b8b46f2b6f91216677
RUN set -x \
    && git clone https://github.com/jedisct1/libsodium.git -b ${SODIUM_VERSION} \
    && cd libsodium \
    && test `git rev-parse HEAD` = ${SODIUM_HASH} || exit 1 \
    && ./autogen.sh \
    && CC=clang CXX=clang++ ./configure --prefix=${PREFIX} --host=x86_64-linux-android --enable-static --disable-shared \
    && make  -j${NPROC} \
    && make install

# libexpat (required by libunbound)
ARG LIBEXPAT_VERSION=R_2_4_8
ARG LIBEXPAT_HASH=3bab6c09bbe8bf42d84b81563ddbcf4cca4be838
RUN set -x \
    && git clone https://github.com/libexpat/libexpat.git -b ${LIBEXPAT_VERSION} \
    && cd libexpat/expat \
    && test `git rev-parse HEAD` = ${LIBEXPAT_HASH} || exit 1 \
    && ./buildconf.sh \
    && CC=clang CXX=clang++ ./configure --prefix=${PREFIX} --host=x86_64-linux-android --enable-static --disable-shared \
    && make  -j${NPROC} \
    && make install

# libunbound
ARG LIBUNBOUND_VERSION=release-1.16.1
ARG LIBUNBOUND_HASH=903538c76e1d8eb30d0814bb55c3ef1ea28164e8
RUN git clone https://github.com/NLnetLabs/unbound.git -b ${LIBUNBOUND_VERSION}
RUN set -x \
    && cd unbound \
    && test `git rev-parse HEAD` = ${LIBUNBOUND_HASH} || exit 1 \
    && CC=clang CXX=clang++ ./configure --prefix=${PREFIX} --host=x86_64-linux-android --enable-static --disable-shared --disable-flto --with-ssl=${PREFIX} --with-libexpat=${PREFIX} \
    && make  -j${NPROC} \
    && make install

COPY . /src
RUN set -x \
    && cd /src \
    && CMAKE_INCLUDE_PATH="${PREFIX}/include" \
       CMAKE_LIBRARY_PATH="${PREFIX}/lib" \
       ANDROID_STANDALONE_TOOLCHAIN_PATH=${TOOLCHAIN_DIR} \
       USE_SINGLE_BUILDDIR=1 \
       PATH=${HOST_PATH} make release-static-android-x86_64-wallet_api -j${NPROC}

RUN set -x \
    && cd /src/build/release \
    && find . -path ./lib -prune -o -name '*.a' -exec cp '{}' lib \;
