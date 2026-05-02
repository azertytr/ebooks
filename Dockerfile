# Build stage — compile APK
FROM eclipse-temurin:17-jdk-jammy as builder

ARG VERSION_CODE=1
ARG VERSION_NAME=1.0.0

ENV ANDROID_HOME=/opt/android-sdk \
    PATH=${PATH}:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools

# Install dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# Download Android SDK
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -L https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp && \
    mv /tmp/cmdline-tools/* ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept Android licenses and install SDK
RUN yes | sdkmanager --licenses && \
    sdkmanager --no_https \
      "platforms;android-34" \
      "build-tools;34.0.0" \
      "platform-tools" \
      "tools"

# Copy project
WORKDIR /app
COPY . .

# Make gradlew executable
RUN chmod +x gradlew

# Build APK (unsigned by default, can be signed via build args)
RUN ./gradlew assembleRelease --no-daemon --build-cache \
    -PVERSION_CODE="${VERSION_CODE}" \
    -PVERSION_NAME="${VERSION_NAME}"

# Export stage — extract built APK
FROM alpine:latest

COPY --from=builder /app/app/build/outputs/apk/release /out

CMD ["sh", "-c", "ls -lh /out/"]
