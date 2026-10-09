FROM bellsoft/liberica-openjre-alpine:25
RUN apk add --no-cache tini
WORKDIR /app
COPY target/anzu-*.jar /app/anzu.jar
EXPOSE 8080
ENTRYPOINT ["/sbin/tini", "--", "java", "-jar", "/app/anzu.jar"]
