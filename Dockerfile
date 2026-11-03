FROM gcr.io/distroless/java25-debian13:nonroot@sha256:5d3177252775d4b01603470fe21c015393019089904f68724bd37242e4bc6e6d

WORKDIR /app
COPY target/anzu-*.jar app.jar

EXPOSE 8080
# The base image's ENTRYPOINT ["java", "-jar"] would swallow any JVM flags, so give the full command.
# --enable-native-access: grpc-netty-shaded (via spring-cloud-gcp pubsub) uses Unsafe/native on JDK 25.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
