FROM clojure:temurin-21-tools-deps

WORKDIR /app
COPY deps.edn /app/deps.edn
COPY src /app/src

ENV COORDINATOR_PORT=8080
EXPOSE 8080

CMD ["clojure", "-M:coordinator"]