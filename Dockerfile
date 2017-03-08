FROM discoenv/clojure-base:master

ENV CONF_TEMPLATE=/usr/src/app/info-typer.properties.tmpl
ENV CONF_FILENAME=info-typer.properties
ENV PROGRAM=info-typer

VOLUME ["/etc/iplant/de"]

COPY project.clj /usr/src/app/
RUN lein deps

COPY conf/main/logback.xml /usr/src/app/
COPY . /usr/src/app

RUN lein uberjar && \
    cp target/info-typer-standalone.jar .

RUN ln -s "/usr/bin/java" "/bin/info-typer"

ENTRYPOINT ["run-service", "-Dlogback.configurationFile=/etc/iplant/de/logging/info-typer-logging.xml", "-cp", ".:info-typer-standalone.jar", "info_typer.core"]

ARG git_commit=unknown
ARG version=unknown
ARG descriptive_version=unknown

LABEL org.cyverse.git-ref="$git_commit"
LABEL org.cyverse.version="$version"
LABEL org.cyverse.descriptive-version="$descriptive_version"
LABEL org.label-schema.vcs-ref="$git_commit"
LABEL org.label-schema.vcs-url="https://github.com/cyverse-de/info-typer"
LABEL org.label-schema.version="$descriptive_version"
