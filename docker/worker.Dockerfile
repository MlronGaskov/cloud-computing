FROM clojure:temurin-21-tools-deps

WORKDIR /app
COPY deps.edn /app/deps.edn
COPY src /app/src

ENV WORKER_PORT=8090
EXPOSE 8090

CMD ["clojure", "-M:worker"]